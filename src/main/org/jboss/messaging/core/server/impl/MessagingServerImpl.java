/*
 * JBoss, Home of Professional Open Source Copyright 2005-2008, Red Hat Middleware LLC, and individual contributors by
 * the @authors tag. See the copyright.txt in the distribution for a full listing of individual contributors. This is
 * free software; you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of the License, or (at your option) any later version.
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details. You should have received a copy of the GNU Lesser General Public License along with this software; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF
 * site: http://www.fsf.org.
 */

package org.jboss.messaging.core.server.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.jboss.messaging.core.client.impl.ClientSessionFactoryImpl;
import org.jboss.messaging.core.config.Configuration;
import org.jboss.messaging.core.config.TransportConfiguration;
import org.jboss.messaging.core.exception.MessagingException;
import org.jboss.messaging.core.logging.Logger;
import org.jboss.messaging.core.management.ManagementService;
import org.jboss.messaging.core.management.MessagingServerControlMBean;
import org.jboss.messaging.core.paging.PagingManager;
import org.jboss.messaging.core.paging.impl.PagingManagerImpl;
import org.jboss.messaging.core.paging.impl.PagingStoreFactoryNIO;
import org.jboss.messaging.core.persistence.StorageManager;
import org.jboss.messaging.core.postoffice.PostOffice;
import org.jboss.messaging.core.postoffice.impl.PostOfficeImpl;
import org.jboss.messaging.core.remoting.Channel;
import org.jboss.messaging.core.remoting.ChannelHandler;
import org.jboss.messaging.core.remoting.RemotingConnection;
import org.jboss.messaging.core.remoting.RemotingService;
import org.jboss.messaging.core.remoting.impl.RemotingConnectionImpl;
import org.jboss.messaging.core.remoting.impl.wireformat.CreateSessionResponseMessage;
import org.jboss.messaging.core.remoting.impl.wireformat.ReattachSessionResponseMessage;
import org.jboss.messaging.core.remoting.spi.Connection;
import org.jboss.messaging.core.remoting.spi.ConnectionLifeCycleListener;
import org.jboss.messaging.core.remoting.spi.ConnectorFactory;
import org.jboss.messaging.core.security.JBMSecurityManager;
import org.jboss.messaging.core.security.Role;
import org.jboss.messaging.core.security.SecurityStore;
import org.jboss.messaging.core.security.impl.SecurityStoreImpl;
import org.jboss.messaging.core.server.MessagingServer;
import org.jboss.messaging.core.server.Queue;
import org.jboss.messaging.core.server.QueueFactory;
import org.jboss.messaging.core.server.ServerSession;
import org.jboss.messaging.core.server.cluster.ClusterManager;
import org.jboss.messaging.core.server.cluster.impl.ClusterManagerImpl;
import org.jboss.messaging.core.settings.HierarchicalRepository;
import org.jboss.messaging.core.settings.impl.HierarchicalObjectRepository;
import org.jboss.messaging.core.settings.impl.QueueSettings;
import org.jboss.messaging.core.transaction.ResourceManager;
import org.jboss.messaging.core.transaction.impl.ResourceManagerImpl;
import org.jboss.messaging.core.version.Version;
import org.jboss.messaging.util.ExecutorFactory;
import org.jboss.messaging.util.JBMThreadFactory;
import org.jboss.messaging.util.OrderedExecutorFactory;
import org.jboss.messaging.util.VersionLoader;

/**
 * The messaging server implementation
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @author <a href="mailto:ataylor@redhat.com>Andy Taylor</a>
 * @version <tt>$Revision: 3543 $</tt> <p/> $Id: ServerPeer.java 3543 2008-01-07 22:31:58Z clebert.suconic@jboss.com $
 */
public class MessagingServerImpl implements MessagingServer
{
   // Constants
   // ------------------------------------------------------------------------------------

   private static final Logger log = Logger.getLogger(MessagingServerImpl.class);

   // Static
   // ---------------------------------------------------------------------------------------

   // Attributes
   // -----------------------------------------------------------------------------------

   private final Version version;

   private volatile boolean started;

   // wired components

   private SecurityStore securityStore;

   private final HierarchicalRepository<QueueSettings> queueSettingsRepository = new HierarchicalObjectRepository<QueueSettings>();

   private ScheduledExecutorService scheduledExecutor;

   private QueueFactory queueFactory;

   private PagingManager pagingManager;

   private PostOffice postOffice;

   private final ExecutorService asyncDeliveryPool = Executors.newCachedThreadPool(new JBMThreadFactory("JBM-async-session-delivery-threads"));

   private final ExecutorFactory executorFactory = new OrderedExecutorFactory(asyncDeliveryPool);

   private HierarchicalRepository<Set<Role>> securityRepository;

   private ResourceManager resourceManager;

   private MessagingServerControlMBean serverManagement;

   private final Map<String, ServerSession> sessions = new ConcurrentHashMap<String, ServerSession>();

   private ClusterManager clusterManager;

   private ConnectorFactory backupConnectorFactory;

   private Map<String, Object> backupConnectorParams;

   // plugins

   private StorageManager storageManager;

   private RemotingService remotingService;

   private JBMSecurityManager securityManager;

   private Configuration configuration;

   private ManagementService managementService;

   // Constructors
   // ---------------------------------------------------------------------------------

   public MessagingServerImpl()
   {
      // We need to hard code the version information into a source file

      version = VersionLoader.getVersion();
   }

   // lifecycle methods
   // ----------------------------------------------------------------

   public synchronized void start() throws Exception
   {
      if (started)
      {
         return;
      }

      /*
       * The following components are pluggable on the messaging server: Configuration, StorageManager, RemotingService,
       * SecurityManager and ManagementRegistration They must already be injected by the time the messaging server
       * starts It's up to the user to make sure the pluggable components are started - their lifecycle will not be
       * controlled here
       */

      // We make sure the pluggable components have been injected
      if (configuration == null)
      {
         throw new IllegalStateException("Must inject Configuration before starting MessagingServer");
      }

      if (storageManager == null)
      {
         throw new IllegalStateException("Must inject StorageManager before starting MessagingServer");
      }

      if (remotingService == null)
      {
         throw new IllegalStateException("Must inject RemotingService before starting MessagingServer");
      }

      if (securityManager == null)
      {
         throw new IllegalStateException("Must inject SecurityManager before starting MessagingServer");
      }

      if (managementService == null)
      {
         throw new IllegalStateException("Must inject ManagementRegistration before starting MessagingServer");
      }

      if (!storageManager.isStarted())
      {
         throw new IllegalStateException("StorageManager must be started before MessagingServer is started");
      }

      if (!remotingService.isStarted())
      {
         throw new IllegalStateException("RemotingService must be started before MessagingServer is started");
      }

      // The rest of the components are not pluggable and created and started
      // here

      securityStore = new SecurityStoreImpl(configuration.getSecurityInvalidationInterval(),
                                            configuration.isSecurityEnabled());
      queueSettingsRepository.setDefault(new QueueSettings());
      scheduledExecutor = new ScheduledThreadPoolExecutor(configuration.getScheduledThreadPoolMaxSize(),
                                                          new JBMThreadFactory("JBM-scheduled-threads"));
      queueFactory = new QueueFactoryImpl(scheduledExecutor, queueSettingsRepository);

      pagingManager = new PagingManagerImpl(new PagingStoreFactoryNIO(configuration.getPagingDirectory()),
                                            storageManager,
                                            queueSettingsRepository,
                                            configuration.getPagingMaxGlobalSizeBytes(),
                                            configuration.getPagingDefaultSize(),
                                            configuration.isJournalSyncNonTransactional());
      pagingManager.start();

      resourceManager = new ResourceManagerImpl((int)configuration.getTransactionTimeout() / 1000,
                                                configuration.getTransactionTimeoutScanPeriod(),
                                                storageManager,
                                                postOffice,
                                                queueSettingsRepository);
      postOffice = new PostOfficeImpl(storageManager,
                                      pagingManager,
                                      queueFactory,
                                      managementService,
                                      queueSettingsRepository,
                                      configuration.getMessageExpiryScanPeriod(),
                                      configuration.getMessageExpiryThreadPriority(),
                                      configuration.isRequireDestinations(),
                                      resourceManager,
                                      configuration.isWildcardRoutingEnabled(),
                                      configuration.isBackup(),
                                      configuration.getIDCacheSize(),
                                      configuration.isPersistIDCache());

      securityRepository = new HierarchicalObjectRepository<Set<Role>>();
      securityRepository.setDefault(new HashSet<Role>());
      securityStore.setSecurityRepository(securityRepository);
      securityStore.setSecurityManager(securityManager);

      postOffice.start();

      resourceManager.start();

      // FIXME the destination corresponding to the notification address is always created
      // so that queues can be created wether the address is allowable or not (to revisit later)
      if (!postOffice.containsDestination(configuration.getManagementNotificationAddress()))
      {
         postOffice.addDestination(configuration.getManagementNotificationAddress(), true);
      }

      String backupConnectorName = configuration.getBackupConnectorName();

      if (backupConnectorName != null)
      {
         TransportConfiguration backupConnector = configuration.getConnectorConfigurations().get(backupConnectorName);

         if (backupConnector == null)
         {
            log.warn("connector with name '" + backupConnectorName + "' is not defined in the configuration.");
         }
         else
         {

            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            try
            {
               Class<?> clz = loader.loadClass(backupConnector.getFactoryClassName());
               backupConnectorFactory = (ConnectorFactory)clz.newInstance();
            }
            catch (Exception e)
            {
               throw new IllegalArgumentException("Error instantiating interceptor \"" + backupConnector.getFactoryClassName() +
                                                           "\"",
                                                  e);
            }

            backupConnectorParams = backupConnector.getParams();
         }
      }
      remotingService.setMessagingServer(this);

      if (configuration.isClustered())
      {
         clusterManager = new ClusterManagerImpl(executorFactory,
                                                 storageManager,
                                                 postOffice,
                                                 queueSettingsRepository,
                                                 scheduledExecutor,
                                                 configuration);

         clusterManager.start();
      }
      
      serverManagement = managementService.registerServer(postOffice,
                                                          storageManager,
                                                          configuration,
                                                          queueSettingsRepository,
                                                          securityRepository,
                                                          resourceManager,
                                                          remotingService,
                                                          this);


      log.info("Started messaging server");

      started = true;
   }

   public synchronized void stop() throws Exception
   {
      if (!started)
      {
         return;
      }

      if (clusterManager != null)
      {
         clusterManager.stop();
      }

      asyncDeliveryPool.shutdown();

      try
      {
         if (!asyncDeliveryPool.awaitTermination(10000, TimeUnit.MILLISECONDS))
         {
            log.warn("Timed out waiting for pool to terminate");
         }
      }
      catch (InterruptedException e)
      {
         // Ignore
      }

      securityStore = null;
      resourceManager.stop();
      resourceManager = null;
      postOffice.stop();
      postOffice = null;
      securityRepository = null;
      securityStore = null;
      queueSettingsRepository.clear();
      scheduledExecutor.shutdown();
      queueFactory = null;
      resourceManager = null;
      serverManagement = null;

      started = false;
   }

   // MessagingServer implementation
   // -----------------------------------------------------------

   // The plugabble components

   public void setConfiguration(final Configuration configuration)
   {
      if (started)
      {
         throw new IllegalStateException("Cannot set configuration when started");
      }

      this.configuration = configuration;
   }

   public Configuration getConfiguration()
   {
      return configuration;
   }

   public void setRemotingService(final RemotingService remotingService)
   {
      if (started)
      {
         throw new IllegalStateException("Cannot set remoting service when started");
      }
      this.remotingService = remotingService;
   }

   public RemotingService getRemotingService()
   {
      return remotingService;
   }

   public void setStorageManager(final StorageManager storageManager)
   {
      if (started)
      {
         throw new IllegalStateException("Cannot set storage manager when started");
      }
      this.storageManager = storageManager;
   }

   public StorageManager getStorageManager()
   {
      return storageManager;
   }

   public void setSecurityManager(final JBMSecurityManager securityManager)
   {
      if (started)
      {
         throw new IllegalStateException("Cannot set security Manager when started");
      }

      this.securityManager = securityManager;
   }

   public JBMSecurityManager getSecurityManager()
   {
      return securityManager;
   }

   public void setManagementService(final ManagementService managementService)
   {
      if (started)
      {
         throw new IllegalStateException("Cannot set management service when started");
      }
      this.managementService = managementService;
   }

   public ManagementService getManagementService()
   {
      return managementService;
   }

   // This is needed for the security deployer
   public HierarchicalRepository<Set<Role>> getSecurityRepository()
   {
      return securityRepository;
   }

   // This is needed for the queue settings deployer
   public HierarchicalRepository<QueueSettings> getQueueSettingsRepository()
   {
      return queueSettingsRepository;
   }

   public ResourceManager getResourceManager()
   {
      return resourceManager;
   }

   public Version getVersion()
   {
      return version;
   }

   public boolean isStarted()
   {
      return started;
   }
   
   public ClusterManager getClusterManager()
   {
      return clusterManager;
   }

   private synchronized void checkActivate(final RemotingConnection connection)
   {
      if (configuration.isBackup())
      {
         freezeAllBackupConnections();

         List<Queue> toActivate = postOffice.activate();

         for (Queue queue : toActivate)
         {
            scheduledExecutor.schedule(new ActivateRunner(queue),
                                       configuration.getQueueActivationTimeout(),
                                       TimeUnit.MILLISECONDS);
         }

         configuration.setBackup(false);

         remotingService.setBackup(false);
      }

      connection.activate();
   }

   // We need to prevent any more packets being handled on any connections (from live) as soon as first live connection
   // is created or re-attaches, to prevent a situation like the following:
   // connection 1 create queue A
   // connection 2 fails over
   // A gets activated since no consumers
   // connection 1 create consumer on A
   // connection 1 delivery
   // connection 1 delivery gets replicated
   // can't find message in queue since active was delivered immediately
   private void freezeAllBackupConnections()
   {
      Set<RemotingConnection> connections = new HashSet<RemotingConnection>();

      for (ServerSession session : sessions.values())
      {
         connections.add(session.getChannel().getConnection());
      }

      for (RemotingConnection connection : connections)
      {
         connection.freeze();
      }
   }

   public ReattachSessionResponseMessage reattachSession(final RemotingConnection connection,
                                                         final String name,
                                                         final int lastReceivedCommandID) throws Exception
   {
      ServerSession session = sessions.get(name);

      // Need to activate the connection even if session can't be found - since otherwise response
      // will never get back

      checkActivate(connection);

      if (session == null)
      {
         return new ReattachSessionResponseMessage(-1, true);
      }
      else
      {
         // Reconnect the channel to the new connection
         int serverLastReceivedCommandID = session.transferConnection(connection, lastReceivedCommandID);

         return new ReattachSessionResponseMessage(serverLastReceivedCommandID, false);
      }
   }

   public CreateSessionResponseMessage replicateCreateSession(final String name,
                                                              final long channelID,
                                                              final String username,
                                                              final String password,
                                                              final int minLargeMessageSize,
                                                              final int incrementingVersion,
                                                              final RemotingConnection connection,
                                                              final boolean autoCommitSends,
                                                              final boolean autoCommitAcks,
                                                              final boolean preAcknowledge,
                                                              final boolean xa,
                                                              final int sendWindowSize) throws Exception
   {
      return doCreateSession(name,
                             channelID,
                             username,
                             password,
                             minLargeMessageSize,
                             incrementingVersion,
                             connection,
                             autoCommitSends,
                             autoCommitAcks,
                             preAcknowledge,
                             xa,
                             sendWindowSize);
   }

   public CreateSessionResponseMessage createSession(final String name,
                                                     final long channelID,
                                                     final String username,
                                                     final String password,
                                                     final int minLargeMessageSize,
                                                     final int incrementingVersion,
                                                     final RemotingConnection connection,
                                                     final boolean autoCommitSends,
                                                     final boolean autoCommitAcks,
                                                     final boolean preAcknowledge,
                                                     final boolean xa,
                                                     final int sendWindowSize) throws Exception
   {
      checkActivate(connection);

      return doCreateSession(name,
                             channelID,
                             username,
                             password,
                             minLargeMessageSize,
                             incrementingVersion,
                             connection,
                             autoCommitSends,
                             autoCommitAcks,
                             preAcknowledge,
                             xa,
                             sendWindowSize);
   }

   public void removeSession(final String name) throws Exception
   {
      sessions.remove(name);
   }

   public List<ServerSession> getSessions(final String connectionID)
   {
      Set<Entry<String, ServerSession>> sessionEntries = sessions.entrySet();
      List<ServerSession> matchingSessions = new ArrayList<ServerSession>();
      for (Entry<String, ServerSession> sessionEntry : sessionEntries)
      {
         ServerSession serverSession = sessionEntry.getValue();
         if (serverSession.getConnectionID().toString().equals(connectionID))
         {
            matchingSessions.add(serverSession);
         }
      }
      return matchingSessions;
   }

   public RemotingConnection getReplicatingConnection()
   {
      // Note we must always get a new connection each time - since there must
      // be a one to one correspondence
      // between connections to clients and replicating connections, since we
      // need to preserve channel ids
      // before and after failover

      if (backupConnectorFactory != null)
      {
         NoCacheConnectionLifeCycleListener listener = new NoCacheConnectionLifeCycleListener();
         
         RemotingConnectionImpl replicatingConnection = (RemotingConnectionImpl)RemotingConnectionImpl.createConnection(backupConnectorFactory,
                                                                                                                        backupConnectorParams,
                                                                                                                        ClientSessionFactoryImpl.DEFAULT_CALL_TIMEOUT,
                                                                                                                        ClientSessionFactoryImpl.DEFAULT_PING_PERIOD,
                                                                                                                        ClientSessionFactoryImpl.DEFAULT_CONNECTION_TTL,
                                                                                                                        scheduledExecutor,
                                                                                                                        listener);

         listener.conn = replicatingConnection;

         replicatingConnection.startPinger();

         return replicatingConnection;
      }
      else
      {
         return null;
      }

   }

   public MessagingServerControlMBean getServerManagement()
   {
      return serverManagement;
   }

   public int getConnectionCount()
   {
      return remotingService.getConnections().size();
   }

   public PostOffice getPostOffice()
   {
      return postOffice;
   }

   // Public
   // ---------------------------------------------------------------------------------------

   // Package protected
   // ----------------------------------------------------------------------------

   // Protected
   // ------------------------------------------------------------------------------------

   // Private
   // --------------------------------------------------------------------------------------

   private CreateSessionResponseMessage doCreateSession(final String name,
                                                        final long channelID,
                                                        final String username,
                                                        final String password,
                                                        final int minLargeMessageSize,
                                                        final int incrementingVersion,
                                                        final RemotingConnection connection,
                                                        final boolean autoCommitSends,
                                                        final boolean autoCommitAcks,
                                                        final boolean preAcknowledge,
                                                        final boolean xa,
                                                        final int sendWindowSize) throws Exception
   {
      if (version.getIncrementingVersion() < incrementingVersion)
      {
         throw new MessagingException(MessagingException.INCOMPATIBLE_CLIENT_SERVER_VERSIONS,
                                      "client not compatible with version: " + version.getFullVersion());
      }

      // Is this comment relevant any more ?

      // Authenticate. Successful autentication will place a new SubjectContext
      // on thread local,
      // which will be used in the authorization process. However, we need to
      // make sure we clean
      // up thread local immediately after we used the information, otherwise
      // some other people
      // security my be screwed up, on account of thread local security stack
      // being corrupted.

      securityStore.authenticate(username, password);

      ServerSession currentSession = sessions.remove(name);

      if (currentSession != null)
      {
         // This session may well be on a different connection and different channel id, so we must get rid
         // of it and create another
         currentSession.getChannel().close();
      }

      Channel channel = connection.getChannel(channelID, sendWindowSize, false);

      final ServerSessionImpl session = new ServerSessionImpl(name,
                                                              channelID,
                                                              username,
                                                              password,
                                                              minLargeMessageSize,
                                                              autoCommitSends,
                                                              autoCommitAcks,
                                                              preAcknowledge,
                                                              xa,
                                                              connection,
                                                              storageManager,
                                                              postOffice,
                                                              queueSettingsRepository,
                                                              resourceManager,
                                                              securityStore,
                                                              executorFactory.getExecutor(),
                                                              channel,
                                                              managementService,
                                                              this,
                                                              configuration.getManagementAddress());

      sessions.put(name, session);

      ChannelHandler handler = new ServerSessionPacketHandler(session, channel);

      channel.setHandler(handler);

      connection.addFailureListener(session);

      return new CreateSessionResponseMessage(version.getIncrementingVersion());
   }

   // Inner classes
   // --------------------------------------------------------------------------------

   private class ActivateRunner implements Runnable
   {
      private Queue queue;

      ActivateRunner(final Queue queue)
      {
         this.queue = queue;
      }

      public void run()
      {
         queue.activateNow(asyncDeliveryPool);
      }
   }

   private static class NoCacheConnectionLifeCycleListener implements ConnectionLifeCycleListener
   {
      private RemotingConnection conn;

      public void connectionCreated(final Connection connection)
      {
      }

      public void connectionDestroyed(final Object connectionID)
      {
         if (conn != null)
         {
            conn.destroy();
         }
      }

      public void connectionException(final Object connectionID, final MessagingException me)
      {
         if (conn != null)
         {
            conn.fail(me);
         }
      }
   }

}
