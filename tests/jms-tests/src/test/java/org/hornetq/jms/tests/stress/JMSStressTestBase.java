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

package org.hornetq.jms.tests.stress;

import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.Topic;
import javax.jms.XASession;

import org.hornetq.jms.tests.HornetQServerTestCase;
import org.hornetq.jms.tests.util.ProxyAssertSupport;

/**
 * 
 * Base class for stress tests
 * 
 * @author <a href="tim.fox@jboss.com">Tim Fox</a>
 * @version <tt>$Revision$</tt>
 *
 * $Id$
*/
public class JMSStressTestBase extends HornetQServerTestCase
{
   protected static final int NUM_PERSISTENT_MESSAGES = 4000;

   protected static final int NUM_NON_PERSISTENT_MESSAGES = 6000;

   protected static final int NUM_PERSISTENT_PRESEND = 5000;

   protected static final int NUM_NON_PERSISTENT_PRESEND = 3000;

   protected ConnectionFactory cf;

   protected Destination topic;

   protected Destination queue1;

   protected Destination queue2;

   protected Destination queue3;

   protected Destination queue4;

   protected Topic topic1;

   protected Topic topic2;

   protected Topic topic3;

   protected Topic topic4;

   @Override
   public void tearDown() throws Exception
   {
      if (checkNoMessageData())
      {
         ProxyAssertSupport.fail("Message data still exists");
      }
      super.tearDown();
   }

   protected void runRunners(final Runner[] runners) throws Exception
   {
      Thread[] threads = new Thread[runners.length];
      for (int i = 0; i < runners.length; i++)
      {
         threads[i] = new Thread(runners[i]);
         threads[i].start();
      }

      for (int i = 0; i < runners.length; i++)
      {
         threads[i].join();
      }

      for (int i = 0; i < runners.length; i++)
      {
         if (runners[i].isFailed())
         {
            ProxyAssertSupport.fail("Runner " + i + " failed");
            log.error("runner failed");
         }
      }
   }

   protected void tweakXASession(final XASession sess)
   {

   }
}
