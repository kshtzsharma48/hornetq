/*
 * Copyright 2009 Red Hat, Inc.
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.hornetq.jms.tests.message;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.XAConnection;
import javax.jms.XASession;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionManagerImple;

import org.hornetq.jms.tests.HornetQServerTestCase;
import org.hornetq.jms.tests.util.ProxyAssertSupport;

/**
 *
 * A JMSXDeliveryCountTest

 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @version <tt>$Revision$</tt>
 *
 * $Id$
 *
 */
public class JMSXDeliveryCountTest extends HornetQServerTestCase
{
   // Constants ------------------------------------------------------------------------------------

   // Static ---------------------------------------------------------------------------------------

   // Attributes -----------------------------------------------------------------------------------

   // Constructors ---------------------------------------------------------------------------------

   // Public ---------------------------------------------------------------------------------------

   public void testSimpleJMSXDeliveryCount() throws Exception
   {
      Connection conn = null;

      try
      {
         conn = getConnectionFactory().createConnection();
         Session s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer p = s.createProducer(HornetQServerTestCase.queue1);
         p.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

         p.send(s.createTextMessage("xoxo"));

         s.close();

         s = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         MessageConsumer c = s.createConsumer(HornetQServerTestCase.queue1);

         conn.start();

         TextMessage tm = (TextMessage)c.receive(1000);

         ProxyAssertSupport.assertEquals("xoxo", tm.getText());
         ProxyAssertSupport.assertTrue("JMSXDeliveryCount is supposed to exist as a property",
                                       tm.propertyExists("JMSXDeliveryCount"));
         ProxyAssertSupport.assertEquals(1, tm.getIntProperty("JMSXDeliveryCount"));

         s.recover();

         tm = (TextMessage)c.receive(1000);

         ProxyAssertSupport.assertEquals("xoxo", tm.getText());
         ProxyAssertSupport.assertTrue("JMSXDeliveryCount is supposed to exist as a property",
                                       tm.propertyExists("JMSXDeliveryCount"));
         ProxyAssertSupport.assertEquals(2, tm.getIntProperty("JMSXDeliveryCount"));

         tm.acknowledge();

         conn.close();
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }

   public void testJMSXDeliveryCountNotDeliveredMessagesNotUpdated() throws Exception
   {
      Connection conn = null;

      try
      {
         conn = getConnectionFactory().createConnection();

         Session s = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         MessageProducer p = s.createProducer(HornetQServerTestCase.queue1);

         p.send(s.createTextMessage("message1"));
         p.send(s.createTextMessage("message2"));
         p.send(s.createTextMessage("message3"));
         p.send(s.createTextMessage("message4"));
         p.send(s.createTextMessage("message5"));

         MessageConsumer c = s.createConsumer(HornetQServerTestCase.queue1);

         conn.start();

         TextMessage tm = (TextMessage)c.receive(1000);

         ProxyAssertSupport.assertEquals("message1", tm.getText());
         ProxyAssertSupport.assertFalse(tm.getJMSRedelivered());
         ProxyAssertSupport.assertEquals(1, tm.getIntProperty("JMSXDeliveryCount"));

         s.close();

         s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         c = s.createConsumer(HornetQServerTestCase.queue1);

         tm = (TextMessage)c.receive(1000);

         ProxyAssertSupport.assertEquals("message1", tm.getText());
         ProxyAssertSupport.assertTrue(tm.getJMSRedelivered());
         ProxyAssertSupport.assertEquals(2, tm.getIntProperty("JMSXDeliveryCount"));

         tm = (TextMessage)c.receive(1000);

         ProxyAssertSupport.assertEquals("message2", tm.getText());
         ProxyAssertSupport.assertFalse(tm.getJMSRedelivered());
         ProxyAssertSupport.assertEquals(1, tm.getIntProperty("JMSXDeliveryCount"));

         tm = (TextMessage)c.receive(1000);

         ProxyAssertSupport.assertEquals("message3", tm.getText());
         ProxyAssertSupport.assertFalse(tm.getJMSRedelivered());
         ProxyAssertSupport.assertEquals(1, tm.getIntProperty("JMSXDeliveryCount"));

         tm = (TextMessage)c.receive(1000);

         ProxyAssertSupport.assertEquals("message4", tm.getText());
         ProxyAssertSupport.assertFalse(tm.getJMSRedelivered());
         ProxyAssertSupport.assertEquals(1, tm.getIntProperty("JMSXDeliveryCount"));

         tm = (TextMessage)c.receive(1000);

         ProxyAssertSupport.assertEquals("message5", tm.getText());
         ProxyAssertSupport.assertFalse(tm.getJMSRedelivered());
         ProxyAssertSupport.assertEquals(1, tm.getIntProperty("JMSXDeliveryCount"));

         tm.acknowledge();
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }

   public void testRedeliveryOnQueue() throws Exception
   {
      Connection conn = null;

      try
      {
         conn = getConnectionFactory().createConnection();

         Session sess1 = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer prod = sess1.createProducer(HornetQServerTestCase.queue1);

         prod.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

         final int NUM_MESSAGES = 100;

         final int NUM_RECOVERIES = 8;

         for (int i = 0; i < NUM_MESSAGES; i++)
         {
            TextMessage tm = sess1.createTextMessage();
            tm.setText("testing" + i);
            prod.send(tm);
         }

         Session sess2 = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         MessageConsumer cons = sess2.createConsumer(HornetQServerTestCase.queue1);

         conn.start();

         TextMessage tm = null;

         for (int j = 0; j < NUM_RECOVERIES; j++)
         {
            for (int i = 0; i < NUM_MESSAGES; i++)
            {
               tm = (TextMessage)cons.receive(3000);
               ProxyAssertSupport.assertNotNull(tm);
               ProxyAssertSupport.assertEquals("testing" + i, tm.getText());
               ProxyAssertSupport.assertTrue("JMSXDeliveryCount is supposed to exist as a property",
                                             tm.propertyExists("JMSXDeliveryCount"));
               ProxyAssertSupport.assertEquals(j + 1, tm.getIntProperty("JMSXDeliveryCount"));
            }
            if (j != NUM_RECOVERIES - 1)
            {
               sess2.recover();
            }
         }

         tm.acknowledge();
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }

   public void testRedeliveryOnTopic() throws Exception
   {
      Connection conn = null;

      try
      {
         conn = getConnectionFactory().createConnection();

         conn.setClientID("myclientid");

         Session sess1 = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         Session sess2 = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         Session sess3 = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         MessageConsumer cons1 = sess1.createConsumer(HornetQServerTestCase.topic1);
         MessageConsumer cons2 = sess2.createConsumer(HornetQServerTestCase.topic1);
         MessageConsumer cons3 = sess3.createDurableSubscriber(HornetQServerTestCase.topic1, "subxyz");

         conn.start();

         final int NUM_MESSAGES = 100;
         final int NUM_RECOVERIES = 9;

         Receiver r1 = new Receiver("R1", sess1, cons1, NUM_MESSAGES, NUM_RECOVERIES);
         Receiver r2 = new Receiver("R2", sess2, cons2, NUM_MESSAGES, NUM_RECOVERIES);
         Receiver r3 = new Receiver("R3", sess3, cons3, NUM_MESSAGES, NUM_RECOVERIES);

         Thread t1 = new Thread(r1);
         Thread t2 = new Thread(r2);
         Thread t3 = new Thread(r3);

         t1.start();
         t2.start();
         t3.start();

         Session sessSend = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer prod = sessSend.createProducer(HornetQServerTestCase.topic1);
         prod.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

         for (int i = 0; i < NUM_MESSAGES; i++)
         {
            TextMessage tm1 = sessSend.createTextMessage("testing" + i);
            prod.send(tm1);
         }

         t1.join();
         t2.join();
         t3.join();

         ProxyAssertSupport.assertFalse(r1.failed);
         ProxyAssertSupport.assertFalse(r2.failed);
         ProxyAssertSupport.assertFalse(r3.failed);

         cons3.close();

         sess3.unsubscribe("subxyz");
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }

   public void testDeliveryCountUpdatedOnCloseTransacted() throws Exception
   {
      Connection conn = null;

      try
      {
         conn = getConnectionFactory().createConnection();

         Session producerSess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer producer = producerSess.createProducer(HornetQServerTestCase.queue1);

         Session consumerSess = conn.createSession(true, Session.SESSION_TRANSACTED);
         MessageConsumer consumer = consumerSess.createConsumer(HornetQServerTestCase.queue1);
         conn.start();

         TextMessage tm = producerSess.createTextMessage("message1");

         producer.send(tm);

         TextMessage rm = (TextMessage)consumer.receive(1000);

         ProxyAssertSupport.assertNotNull(rm);

         ProxyAssertSupport.assertEquals(tm.getText(), rm.getText());

         ProxyAssertSupport.assertTrue("JMSXDeliveryCount is supposed to exist as a property",
                                       tm.propertyExists("JMSXDeliveryCount"));
         ProxyAssertSupport.assertEquals(1, rm.getIntProperty("JMSXDeliveryCount"));

         ProxyAssertSupport.assertFalse(rm.getJMSRedelivered());

         consumerSess.rollback();

         rm = (TextMessage)consumer.receive(1000);

         ProxyAssertSupport.assertNotNull(rm);

         ProxyAssertSupport.assertEquals(tm.getText(), rm.getText());

         ProxyAssertSupport.assertEquals(2, rm.getIntProperty("JMSXDeliveryCount"));

         ProxyAssertSupport.assertTrue(rm.getJMSRedelivered());

         consumerSess.rollback();

         rm = (TextMessage)consumer.receive(1000);

         ProxyAssertSupport.assertNotNull(rm);

         ProxyAssertSupport.assertEquals(tm.getText(), rm.getText());

         ProxyAssertSupport.assertEquals(3, rm.getIntProperty("JMSXDeliveryCount"));

         ProxyAssertSupport.assertTrue(rm.getJMSRedelivered());

         // Now close the session without committing

         consumerSess.close();

         consumerSess = conn.createSession(true, Session.SESSION_TRANSACTED);

         consumer = consumerSess.createConsumer(HornetQServerTestCase.queue1);

         rm = (TextMessage)consumer.receive(1000);

         ProxyAssertSupport.assertNotNull(rm);

         ProxyAssertSupport.assertEquals(tm.getText(), rm.getText());

         ProxyAssertSupport.assertEquals(4, rm.getIntProperty("JMSXDeliveryCount"));

         ProxyAssertSupport.assertTrue(rm.getJMSRedelivered());

         consumerSess.commit();
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }

   public void testDeliveryCountUpdatedOnCloseClientAck() throws Exception
   {
      Connection conn = null;

      try
      {
         conn = getConnectionFactory().createConnection();

         Session producerSess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer producer = producerSess.createProducer(HornetQServerTestCase.queue1);

         Session consumerSess = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         MessageConsumer consumer = consumerSess.createConsumer(HornetQServerTestCase.queue1);
         conn.start();

         TextMessage tm = producerSess.createTextMessage("message1");

         producer.send(tm);

         TextMessage rm = (TextMessage)consumer.receive(1000);

         ProxyAssertSupport.assertNotNull(rm);

         ProxyAssertSupport.assertEquals(tm.getText(), rm.getText());

         ProxyAssertSupport.assertEquals(1, rm.getIntProperty("JMSXDeliveryCount"));

         ProxyAssertSupport.assertFalse(rm.getJMSRedelivered());

         consumerSess.recover();

         rm = (TextMessage)consumer.receive(1000);

         ProxyAssertSupport.assertNotNull(rm);

         ProxyAssertSupport.assertEquals(tm.getText(), rm.getText());

         ProxyAssertSupport.assertEquals(2, rm.getIntProperty("JMSXDeliveryCount"));

         ProxyAssertSupport.assertTrue(rm.getJMSRedelivered());

         consumerSess.recover();

         rm = (TextMessage)consumer.receive(1000);

         ProxyAssertSupport.assertNotNull(rm);

         ProxyAssertSupport.assertEquals(tm.getText(), rm.getText());

         ProxyAssertSupport.assertEquals(3, rm.getIntProperty("JMSXDeliveryCount"));

         ProxyAssertSupport.assertTrue(rm.getJMSRedelivered());

         // Now close the session without committing

         consumerSess.close();

         consumerSess = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);

         consumer = consumerSess.createConsumer(HornetQServerTestCase.queue1);

         rm = (TextMessage)consumer.receive(1000);

         ProxyAssertSupport.assertNotNull(rm);

         ProxyAssertSupport.assertEquals(tm.getText(), rm.getText());

         ProxyAssertSupport.assertEquals(4, rm.getIntProperty("JMSXDeliveryCount"));

         ProxyAssertSupport.assertTrue(rm.getJMSRedelivered());

         rm.acknowledge();
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }

   public void testDeliveryCountUpdatedOnCloseXA() throws Exception
   {
      XAConnection xaConn = null;

      Connection conn = null;
      TransactionManager mgr = new TransactionManagerImple();

      Transaction toResume = null;

      Transaction tx = null;

      try
      {
         toResume = mgr.suspend();

         conn = getConnectionFactory().createConnection();

         // Send a message

         Session producerSess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer producer = producerSess.createProducer(HornetQServerTestCase.queue1);

         TextMessage tm = producerSess.createTextMessage("message1");

         producer.send(tm);

         xaConn = getXAConnectionFactory().createXAConnection();

         XASession consumerSess = xaConn.createXASession();
         MessageConsumer consumer = consumerSess.createConsumer(HornetQServerTestCase.queue1);
         xaConn.start();

         DummyXAResource res = new DummyXAResource();

         mgr.begin();

         tx = mgr.getTransaction();

         tx.enlistResource(res);

         tx.enlistResource(consumerSess.getXAResource());

         TextMessage rm = (TextMessage)consumer.receive(1000);

         ProxyAssertSupport.assertNotNull(rm);

         ProxyAssertSupport.assertEquals(tm.getText(), rm.getText());

         ProxyAssertSupport.assertEquals(1, rm.getIntProperty("JMSXDeliveryCount"));

         ProxyAssertSupport.assertFalse(rm.getJMSRedelivered());

         tx.delistResource(res, XAResource.TMSUCCESS);

         tx.delistResource(consumerSess.getXAResource(), XAResource.TMSUCCESS);

         mgr.rollback();

         mgr.begin();

         tx = mgr.getTransaction();

         tx.enlistResource(res);

         tx.enlistResource(consumerSess.getXAResource());

         rm = (TextMessage)consumer.receive(1000);

         ProxyAssertSupport.assertNotNull(rm);

         ProxyAssertSupport.assertEquals(tm.getText(), rm.getText());

         ProxyAssertSupport.assertEquals(2, rm.getIntProperty("JMSXDeliveryCount"));

         ProxyAssertSupport.assertTrue(rm.getJMSRedelivered());

         tx.delistResource(res, XAResource.TMSUCCESS);

         tx.delistResource(consumerSess.getXAResource(), XAResource.TMSUCCESS);

         mgr.rollback();

         mgr.begin();

         tx = mgr.getTransaction();

         tx.enlistResource(res);

         tx.enlistResource(consumerSess.getXAResource());

         rm = (TextMessage)consumer.receive(1000);

         ProxyAssertSupport.assertNotNull(rm);

         ProxyAssertSupport.assertEquals(tm.getText(), rm.getText());

         ProxyAssertSupport.assertEquals(3, rm.getIntProperty("JMSXDeliveryCount"));

         ProxyAssertSupport.assertTrue(rm.getJMSRedelivered());

         tx.delistResource(res, XAResource.TMSUCCESS);

         tx.delistResource(consumerSess.getXAResource(), XAResource.TMSUCCESS);

         mgr.rollback();

         // Must close consumer first

         consumer.close();

         consumerSess.close();

         consumerSess = xaConn.createXASession();

         consumer = consumerSess.createConsumer(HornetQServerTestCase.queue1);

         mgr.begin();

         tx = mgr.getTransaction();

         tx.enlistResource(res);

         tx.enlistResource(consumerSess.getXAResource());

         rm = (TextMessage)consumer.receive(1000);

         ProxyAssertSupport.assertNotNull(rm);

         ProxyAssertSupport.assertEquals(tm.getText(), rm.getText());

         ProxyAssertSupport.assertEquals(4, rm.getIntProperty("JMSXDeliveryCount"));

         ProxyAssertSupport.assertTrue(rm.getJMSRedelivered());

         tx.delistResource(res, XAResource.TMSUCCESS);

         tx.delistResource(consumerSess.getXAResource(), XAResource.TMSUCCESS);
      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }

         if (tx != null)
         {
            try
            {
               mgr.commit();
            }
            catch (Exception ignore)
            {
            }
         }
         if (xaConn != null)
         {
            xaConn.close();
         }

         if (toResume != null)
         {
            try
            {
               mgr.resume(toResume);
            }
            catch (Exception ignore)
            {
            }
         }
      }
   }

   class Receiver implements Runnable
   {
      MessageConsumer cons;

      int numMessages;

      int numRecoveries;

      boolean failed;

      Session sess;

      String name;

      Receiver(final String name,
               final Session sess,
               final MessageConsumer cons,
               final int numMessages,
               final int numRecoveries)
      {
         this.sess = sess;
         this.cons = cons;
         this.numMessages = numMessages;
         this.numRecoveries = numRecoveries;
         this.name = name;
      }

      public void run()
      {
         try
         {
            Message lastMessage = null;
            for (int j = 0; j < numRecoveries; j++)
            {

               for (int i = 0; i < numMessages; i++)
               {
                  TextMessage tm = (TextMessage)cons.receive();
                  lastMessage = tm;

                  if (tm == null)
                  {
                     failed = true;
                  }

                  if (!tm.getText().equals("testing" + i))
                  {
                     log.error("Out of order!!");
                     failed = true;
                  }

                  if (tm.getIntProperty("JMSXDeliveryCount") != j + 1)
                  {
                     log.error("DeliveryImpl count not expected value:" + (j + 1) +
                               " actual:" +
                               tm.getIntProperty("JMSXDeliveryCount"));;
                     failed = true;
                  }
               }
               if (j != numRecoveries - 1)
               {
                  sess.recover();
               }

            }
            lastMessage.acknowledge();
         }
         catch (Exception e)
         {
            failed = true;
         }
      }
   }

   // Package protected ----------------------------------------------------------------------------

   // Protected ------------------------------------------------------------------------------------

   // Private --------------------------------------------------------------------------------------

   // Inner classes --------------------------------------------------------------------------------

   static class DummyXAResource implements XAResource
   {
      DummyXAResource()
      {
      }

      public void commit(final Xid arg0, final boolean arg1) throws XAException
      {
      }

      public void end(final Xid arg0, final int arg1) throws XAException
      {
      }

      public void forget(final Xid arg0) throws XAException
      {
      }

      public int getTransactionTimeout() throws XAException
      {
         return 0;
      }

      public boolean isSameRM(final XAResource arg0) throws XAException
      {
         return false;
      }

      public int prepare(final Xid arg0) throws XAException
      {
         return XAResource.XA_OK;
      }

      public Xid[] recover(final int arg0) throws XAException
      {
         return null;
      }

      public void rollback(final Xid arg0) throws XAException
      {
      }

      public boolean setTransactionTimeout(final int arg0) throws XAException
      {
         return false;
      }

      public void start(final Xid arg0, final int arg1) throws XAException
      {

      }

   }

}
