package com.example.ipcam.onvif

import android.util.Log
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket

/**
 * WS-Discovery 设备发现（SOAP over UDP 多播 3702）。
 * 响应 Probe（ProbeMatch），并周期发送 Hello 通告设备上线。
 */
class WsDiscoveryServer(private val xAddrsProvider: () -> String) {

    private val multicastAddress = "239.255.255.250"
    private val port = 3702
    private val uuid = "urn:uuid:2419d68a-2dd2-21b2-a205-4A6968FF746b"

    private var socket: MulticastSocket? = null
    private var listenThread: Thread? = null
    private var helloThread: Thread? = null
    private var messageNumber = 1

    @Volatile
    private var running = false

    fun start() {
        try {
            socket = MulticastSocket(port).also {
                it.joinGroup(InetAddress.getByName(multicastAddress))
            }
        } catch (e: Exception) {
            Log.e(TAG, "failed to join multicast group", e)
            return
        }
        running = true
        listenThread = Thread({ listen() }, "WsDiscoveryListen").also { it.start() }
        helloThread = Thread({ sendHelloLoop() }, "WsDiscoveryHello").also { it.start() }
        Log.i(TAG, "WS-Discovery started on $multicastAddress:$port")
    }

    private fun listen() {
        val buf = ByteArray(4096)
        while (running) {
            try {
                val packet = DatagramPacket(buf, buf.size)
                socket?.receive(packet)
                val msg = String(packet.data, 0, packet.length)
                if (msg.contains("Probe", ignoreCase = true)) {
                    sendProbeMatch(packet.address, packet.port, extractMessageId(msg))
                }
            } catch (e: Exception) {
                if (!running) break
            }
        }
    }

    private fun sendProbeMatch(addr: InetAddress, port: Int, messageId: String) {
        val xaddrs = xAddrsProvider()
        val relatesTo = if (messageId.isNotEmpty()) "<wsa:RelatesTo>$messageId</wsa:RelatesTo>" else ""
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<env:Envelope xmlns:env="http://www.w3.org/2003/05/soap-envelope" xmlns:wsa="http://schemas.xmlsoap.org/ws/2004/08/addressing" xmlns:d="http://schemas.xmlsoap.org/ws/2005/04/discovery" xmlns:dn="http://www.onvif.org/ver10/network/wsdl">
<env:Header><wsa:Action>http://schemas.xmlsoap.org/ws/2005/04/discovery/ProbeMatches</wsa:Action>$relatesTo<wsa:To>http://schemas.xmlsoap.org/ws/2004/08/addressing/role/anonymous</wsa:To><d:AppSequence InstanceId="1" MessageNumber="${messageNumber++}"/></env:Header>
<env:Body><d:ProbeMatches><d:ProbeMatch><wsa:EndpointReference><wsa:Address>$uuid</wsa:Address></wsa:EndpointReference><d:Types>dn:NetworkVideoTransmitter</d:Types><d:Scopes>onvif://www.onvif.org/type/video_encoder onvif://www.onvif.org/Profile/Streaming onvif://www.onvif.org/name/IPCam</d:Scopes><d:XAddrs>$xaddrs</d:XAddrs><d:MetadataVersion>1</d:MetadataVersion></d:ProbeMatch></d:ProbeMatches></env:Body>
</env:Envelope>"""
        try {
            socket?.send(DatagramPacket(xml.toByteArray(), xml.length, addr, port))
        } catch (e: Exception) {
            Log.e(TAG, "failed to send ProbeMatch", e)
        }
    }

    private fun sendHelloLoop() {
        while (running) {
            sendHello()
            try {
                Thread.sleep(60_000)
            } catch (e: InterruptedException) {
                break
            }
        }
    }

    private fun sendHello() {
        val xaddrs = xAddrsProvider()
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<env:Envelope xmlns:env="http://www.w3.org/2003/05/soap-envelope" xmlns:wsa="http://schemas.xmlsoap.org/ws/2004/08/addressing" xmlns:d="http://schemas.xmlsoap.org/ws/2005/04/discovery" xmlns:dn="http://www.onvif.org/ver10/network/wsdl">
<env:Header><wsa:Action>http://schemas.xmlsoap.org/ws/2005/04/discovery/Hello</wsa:Action><wsa:To>urn:schemas-xmlsoap-org:ws:2005:04:discovery</wsa:To><d:AppSequence InstanceId="1" MessageNumber="${messageNumber++}"/></env:Header>
<env:Body><d:Hello><wsa:EndpointReference><wsa:Address>$uuid</wsa:Address></wsa:EndpointReference><d:Types>dn:NetworkVideoTransmitter</d:Types><d:Scopes>onvif://www.onvif.org/type/video_encoder onvif://www.onvif.org/Profile/Streaming</d:Scopes><d:XAddrs>$xaddrs</d:XAddrs><d:MetadataVersion>1</d:MetadataVersion></d:Hello></env:Body>
</env:Envelope>"""
        try {
            val addr = InetAddress.getByName(multicastAddress)
            socket?.send(DatagramPacket(xml.toByteArray(), xml.length, addr, port))
        } catch (e: Exception) {
            Log.e(TAG, "failed to send Hello", e)
        }
    }

    private fun extractMessageId(msg: String): String {
        val m = Regex("<wsa:MessageID[^>]*>([^<]+)</wsa:MessageID>").find(msg)
            ?: Regex("<MessageID[^>]*>([^<]+)</MessageID>").find(msg)
        return m?.groupValues?.get(1)?.trim() ?: ""
    }

    fun stop() {
        running = false
        try {
            socket?.leaveGroup(InetAddress.getByName(multicastAddress))
        } catch (_: Exception) {
        }
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
    }

    companion object {
        private const val TAG = "WsDiscoveryServer"
    }
}
