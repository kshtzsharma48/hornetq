/*
 * Copyright 2010 Red Hat, Inc.
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

package org.hornetq.jms.client;

import javax.jms.ConnectionFactory;
import javax.jms.QueueConnectionFactory;
import javax.jms.TopicConnectionFactory;
import javax.jms.XAConnectionFactory;
import javax.jms.XAQueueConnectionFactory;
import javax.jms.XATopicConnectionFactory;

import org.hornetq.api.core.DiscoveryGroupConfiguration;
import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.core.client.ServerLocator;
import org.hornetq.api.jms.JMSFactoryType;

/**
 * A class that represents a XAConnectionFactory.
 * 
 * We consider the XAConnectionFactory to be the most complete possible option. It can be casted to any other connection factory since it is fully functional
 * 
 * @author <a href="mailto:hgao@redhat.com">Howard Gao</a>
 */
public class HornetQXAConnectionFactory extends HornetQConnectionFactory implements XAConnectionFactory, XATopicConnectionFactory, XAQueueConnectionFactory, ConnectionFactory, TopicConnectionFactory, QueueConnectionFactory
{
   private static final long serialVersionUID = 743611571839154115L;

   /**
    * 
    */
   public HornetQXAConnectionFactory()
   {
      super();
   }

   /**
    * @param serverLocator
    */
   public HornetQXAConnectionFactory(ServerLocator serverLocator)
   {
      super(serverLocator);
    }

   /**
    * @param ha
    * @param discoveryAddress
    * @param discoveryPort
    */
   public HornetQXAConnectionFactory(final boolean ha, final DiscoveryGroupConfiguration groupConfiguration)
   {
      super(ha, groupConfiguration);
   }

   /**
    * @param ha
    * @param initialConnectors
    */
   public HornetQXAConnectionFactory(final boolean ha, final TransportConfiguration... initialConnectors)
   {
      super(ha, initialConnectors);
   }

   public int getFactoryType()
   {
      return JMSFactoryType.XA_CF.intValue();
   }

}
