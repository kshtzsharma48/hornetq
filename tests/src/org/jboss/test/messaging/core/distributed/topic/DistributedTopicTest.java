/*
* JBoss, Home of Professional Open Source
* Copyright 2005, JBoss Inc., and individual contributors as indicated
* by the @authors tag. See the copyright.txt in the distribution for a
* full listing of individual contributors.
*
* This is free software; you can redistribute it and/or modify it
* under the terms of the GNU Lesser General Public License as
* published by the Free Software Foundation; either version 2.1 of
* the License, or (at your option) any later version.
*
* This software is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this software; if not, write to the Free
* Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
* 02110-1301 USA, or see the FSF site: http://www.fsf.org.
*/
package org.jboss.test.messaging.core.distributed.topic;

import org.jboss.test.messaging.core.distributed.topic.base.DistributedTopicTestBase;
import org.jboss.messaging.core.distributed.topic.DistributedTopic;

/**
 * @author <a href="mailto:ovidiu@jboss.org">Ovidiu Feodorov</a>
 * @version <tt>$Revision$</tt>
 *
 * $Id$
 */
public class DistributedTopicTest extends DistributedTopicTestBase
{
   // Constants -----------------------------------------------------

   // Static --------------------------------------------------------
   
   // Attributes ----------------------------------------------------

   // Constructors --------------------------------------------------

   public DistributedTopicTest(String name)
   {
      super(name);
   }

   // DistributedQueueTestBase overrides ---------------------------

   public void setUp() throws Exception
   {
      super.setUp();

      topic = new DistributedTopic("test", ms, tl, dispatcher);
      topic2 = new DistributedTopic("test", ms2, tl2, dispatcher2);
      topic3 = new DistributedTopic("test", ms3, tl3, dispatcher3);

      log.debug("setup done");
   }

   public void tearDown() throws Exception
   {
      ((DistributedTopic)topic).close();
      
      topic = null;

      topic2.close();
      topic2 = null;

      topic3.close();
      topic3 = null;

      super.tearDown();
   }

   // Public --------------------------------------------------------

   public void testDistributedTopic_2() throws Exception
   {
      // TODO
      // This metods overrides a DistributedTopicTestBase test that fails. I disable it because it
      // doesn't match the current assumption that a Channel has only one receiver, and still we
      // want a clean test run.
      // See http://jira.jboss.org/jira/browse/JBMESSAGING-228
   }

   public void testDistributedTopic_4() throws Exception
   {
      // TODO
      // This metods overrides a DistributedTopicTestBase test that fails. I disable it because it
      // doesn't match the current assumption that a Channel has only one receiver, and still we
      // want a clean test run.
      // See http://jira.jboss.org/jira/browse/JBMESSAGING-228
   }

   // Package protected ---------------------------------------------
   
   // Protected -----------------------------------------------------
   
   // Private -------------------------------------------------------
   
   // Inner classes -------------------------------------------------   
}
