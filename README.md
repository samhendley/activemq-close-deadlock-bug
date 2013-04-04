## Update

This issue has been addressed by the activemq team with a bug fix and workaround.

https://issues.apache.org/jira/browse/AMQ-4361

## Overview

The deadlock occurs when we are using TcpTransport to a flow-controlled queue and we then try to gracefully shutdown 
the application. The close operation hangs forever because it is trying to send a "close packet" to the server. It never
gets the chance to send that request because the socket is blocked by the publishing thread.
This stops my publisher from shutting down and causes us to orphan threads during shutdown.

I have verified this bug occurs on atleast activemq 5.6.0 and 5.8.0 and on linux and windows using JDK 1.6.

I don't need a fix for the bug necessarily, just a way to gracefully shutdown my publisher if I get into this state.

## Partial Stack Trace During failure

```
"ClosingThread" prio=6 tid=0x045ce000 nid=0xa84 waiting on condition [0x04ddf000]
   java.lang.Thread.State: WAITING (parking)
	at sun.misc.Unsafe.park(Native Method)
	- parking to wait for  <0x23fc52d8> (a java.util.concurrent.locks.ReentrantLock$NonfairSync)
	at java.util.concurrent.locks.LockSupport.park(LockSupport.java:156)
	at java.util.concurrent.locks.AbstractQueuedSynchronizer.parkAndCheckInterrupt(AbstractQueuedSynchronizer.java:811)
	at java.util.concurrent.locks.AbstractQueuedSynchronizer.acquireQueued(AbstractQueuedSynchronizer.java:842)
	at java.util.concurrent.locks.AbstractQueuedSynchronizer.acquire(AbstractQueuedSynchronizer.java:1178)
	at java.util.concurrent.locks.ReentrantLock$NonfairSync.lock(ReentrantLock.java:186)
	at java.util.concurrent.locks.ReentrantLock.lock(ReentrantLock.java:262)
	at org.apache.activemq.transport.MutexTransport.oneway(MutexTransport.java:66)
	at org.apache.activemq.transport.ResponseCorrelator.oneway(ResponseCorrelator.java:60)
	at org.apache.activemq.ActiveMQConnection.doAsyncSendPacket(ActiveMQConnection.java:1304)
	at org.apache.activemq.ActiveMQConnection.asyncSendPacket(ActiveMQConnection.java:1298)
	at org.apache.activemq.ActiveMQSession.asyncSendPacket(ActiveMQSession.java:1901)
	at org.apache.activemq.ActiveMQMessageProducer.close(ActiveMQMessageProducer.java:173)
	at org.activemq.bug.DeadlockDuringCloseTest$2.run(DeadlockDuringCloseTest.java:83)
	at java.lang.Thread.run(Thread.java:662)

"PublishingThread" prio=6 tid=0x045cd800 nid=0xb84 runnable [0x04d8f000]
   java.lang.Thread.State: RUNNABLE
	at java.net.SocketOutputStream.socketWrite0(Native Method)
	at java.net.SocketOutputStream.socketWrite(SocketOutputStream.java:92)
	at java.net.SocketOutputStream.write(SocketOutputStream.java:136)
	at org.apache.activemq.transport.tcp.TcpBufferedOutputStream.flush(TcpBufferedOutputStream.java:115)
	at java.io.DataOutputStream.flush(DataOutputStream.java:106)
	at org.apache.activemq.transport.tcp.TcpTransport.oneway(TcpTransport.java:176)
	at org.apache.activemq.transport.AbstractInactivityMonitor.doOnewaySend(AbstractInactivityMonitor.java:322)
	at org.apache.activemq.transport.AbstractInactivityMonitor.oneway(AbstractInactivityMonitor.java:304)
	at org.apache.activemq.transport.TransportFilter.oneway(TransportFilter.java:85)
	at org.apache.activemq.transport.WireFormatNegotiator.oneway(WireFormatNegotiator.java:104)
	at org.apache.activemq.transport.MutexTransport.oneway(MutexTransport.java:68)
	at org.apache.activemq.transport.ResponseCorrelator.oneway(ResponseCorrelator.java:60)
	at org.apache.activemq.ActiveMQConnection.doAsyncSendPacket(ActiveMQConnection.java:1304)
	at org.apache.activemq.ActiveMQConnection.asyncSendPacket(ActiveMQConnection.java:1298)
	at org.apache.activemq.ActiveMQSession.send(ActiveMQSession.java:1782)
	- locked <0x23faa7d8> (a java.lang.Object)
	at org.apache.activemq.ActiveMQMessageProducer.send(ActiveMQMessageProducer.java:289)
	at org.apache.activemq.ActiveMQMessageProducer.send(ActiveMQMessageProducer.java:224)
	at org.apache.activemq.ActiveMQMessageProducerSupport.send(ActiveMQMessageProducerSupport.java:300)
	at org.activemq.bug.DeadlockDuringCloseTest$1.run(DeadlockDuringCloseTest.java:63)
	at java.lang.Thread.run(Thread.java:662)

```
