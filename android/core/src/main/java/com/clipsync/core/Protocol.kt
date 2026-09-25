package com.clipsync.core
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
enum class MessageType(val id:Int){TEXT(1),HELLO(2),ACK(3),PING(4),IMAGE(5)}
data class ClipMessage(val type:MessageType,val text:String?=null,val bytes:ByteArray?=null,val lamport:Long=0,val deviceId:String?=null)
object FrameCodec { const val MAX=1048576; private val magic="CSP1".toByteArray(); fun encode(m:ClipMessage):ByteArray { val d=m.deviceId ?: ""; val p=when(m.type){MessageType.TEXT ->(m.text ?: "").toByteArray();MessageType.IMAGE ->m.bytes ?: byteArrayOf();MessageType.HELLO ->ByteBuffer.allocate(10+d.toByteArray().size).putLong(m.lamport).putShort(d.toByteArray().size.toShort()).put(d.toByteArray()).array();else ->byteArrayOf()}; require(p.size<=MAX); return magic+byteArrayOf(m.type.id.toByte())+ByteBuffer.allocate(4).putInt(p.size).array()+p } fun decode(f:ByteArray):ClipMessage { require(f.size>=9); require(f.copyOfRange(0,4).contentEquals(magic)); val t=MessageType.entries.firstOrNull{it.id==f[4].toInt()} ?: error("UnknownType"); val n=ByteBuffer.wrap(f,5,4).int; require(n in 0..MAX); require(f.size==9+n); val p=f.copyOfRange(9,f.size); return when(t){MessageType.TEXT->ClipMessage(t,text=p.toString(Charsets.UTF_8));MessageType.IMAGE ->ClipMessage(t,bytes=p);MessageType.HELLO ->ClipMessage(t,lamport=ByteBuffer.wrap(p).long,deviceId=p.copyOfRange(10,p.size).toString(Charsets.UTF_8));else ->ClipMessage(t)} }}
class FrameReader {
    private val header = ByteArray(9)
    private var headerCount = 0
    private var payload: ByteArray? = null
    private var payloadCount = 0
    fun push(input: ByteArray): List<ClipMessage> {
        val output = mutableListOf<ClipMessage>()
        var offset = 0
        while (offset < input.size) {
            if (payload == null) {
                val count = minOf(9 - headerCount, input.size - offset)
                input.copyInto(header, headerCount, offset, offset + count)
                headerCount += count; offset += count
                if (headerCount < 9) continue
                require(header.copyOfRange(0, 4).contentEquals("CSP1".toByteArray())) { "Bad frame magic" }
                require(MessageType.entries.any { it.id == header[4].toInt() }) { "Unknown frame type" }
                val length = ByteBuffer.wrap(header, 5, 4).int
                require(length in 0..FrameCodec.MAX) { "Invalid frame length" }
                payload = ByteArray(length)
            }
            val bytes = payload!!
            val count = minOf(bytes.size - payloadCount, input.size - offset)
            input.copyInto(bytes, payloadCount, offset, offset + count)
            payloadCount += count; offset += count
            if (payloadCount == bytes.size) {
                output += FrameCodec.decode(header + bytes)
                headerCount = 0; payloadCount = 0; payload = null
            }
        }
        return output
    }
}
class LamportClock(var value:Long=0){fun local()=++value;fun observe(r:Long)=run{value=maxOf(value,r)+1;value}}
data class ClipVersion(val lamport:Long,val deviceId:String,val hash:String,val message:ClipMessage):Comparable<ClipVersion>{override fun compareTo(o:ClipVersion)=if(lamport!=o.lamport)lamport.compareTo(o.lamport) else deviceId.compareTo(o.deviceId)}
class PendingSlot{var value:ClipVersion?=null;fun put(v:ClipVersion){if(value==null||v>value!!)value=v};fun take()=value.also{value=null}}
class SendScheduler{var text:ClipVersion?=null;var image:ClipVersion?=null;fun offer(v:ClipVersion){if(v.message.type==MessageType.TEXT){text=v;image=null}else image=v};fun next()=(text?:image).also{if(it?.message?.type==MessageType.TEXT)text=null else image=null}}
fun hash(x:ByteArray)=MessageDigest.getInstance("SHA-256").digest(x).joinToString(""){ "%02X".format(it)}


enum class EngineState{DISCONNECTED,HANDSHAKING,CONNECTED,PAUSED}
class InMemoryTransport{val a=ArrayDeque<ByteArray>();val b=ArrayDeque<ByteArray>();fun send(fromA:Boolean,x:ByteArray){(if(fromA)b else a).add(x)}}
class SyncEngine{val hashGuard=HashGuard();var state=EngineState.DISCONNECTED;val pending=PendingSlot();val scheduler=SendScheduler();val seen=mutableSetOf<String>();fun connect(){state=EngineState.CONNECTED};fun disconnect(){state=EngineState.DISCONNECTED};fun pause(){state=EngineState.PAUSED};fun resume(){state=EngineState.CONNECTED};fun apply(v:ClipVersion):Boolean{if(!hashGuard.shouldApply(v.hash))return false;hashGuard.applied(v.hash);seen.clear();seen.add(v.hash);return true};fun local(v:ClipVersion){if(v.hash==hashGuard.lastAppliedHash)return;if(state==EngineState.CONNECTED)scheduler.offer(v)else pending.put(v)}}
