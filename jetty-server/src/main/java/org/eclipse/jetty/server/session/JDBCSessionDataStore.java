//
//  ========================================================================
//  Copyright (c) 1995-2016 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//


package org.eclipse.jetty.server.session;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jetty.util.ClassLoadingObjectInputStream;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

/**
 * JDBCSessionDataStore
 *
 * Session data stored in database
 */
public class JDBCSessionDataStore extends AbstractSessionDataStore
{
    final static Logger LOG = Log.getLogger("org.eclipse.jetty.server.session");
    
    protected boolean _initialized = false;
    protected Map<String, AtomicInteger> _unloadables = new ConcurrentHashMap<>();

    private DatabaseAdaptor _dbAdaptor;
    private SessionTableSchema _sessionTableSchema;

    private int _attempts = -1; // <= 0 means unlimited attempts to load a session
    private boolean _deleteUnloadables = false; //true means if attempts exhausted delete the session
    private long _gracePeriodMs = 1000L * 60 * 60; //default grace period is 1hr

    /**
     * SessionTableSchema
     *
     */
    public static class SessionTableSchema
    {      
        public final static int MAX_INTERVAL_NOT_SET = -999;
        
        protected DatabaseAdaptor _dbAdaptor;
        protected String _tableName = "JettySessions";
        protected String _idColumn = "sessionId";
        protected String _contextPathColumn = "contextPath";
        protected String _virtualHostColumn = "virtualHost"; 
        protected String _lastNodeColumn = "lastNode";
        protected String _accessTimeColumn = "accessTime"; 
        protected String _lastAccessTimeColumn = "lastAccessTime";
        protected String _createTimeColumn = "createTime";
        protected String _cookieTimeColumn = "cookieTime";
        protected String _lastSavedTimeColumn = "lastSavedTime";
        protected String _expiryTimeColumn = "expiryTime";
        protected String _maxIntervalColumn = "maxInterval";
        protected String _mapColumn = "map";

        
        
        protected void setDatabaseAdaptor(DatabaseAdaptor dbadaptor)
        {
            _dbAdaptor = dbadaptor;
        }
        
        
        public String getTableName()
        {
            return _tableName;
        }
        public void setTableName(String tableName)
        {
            checkNotNull(tableName);
            _tableName = tableName;
        }
     
        public String getIdColumn()
        {
            return _idColumn;
        }
        public void setIdColumn(String idColumn)
        {
            checkNotNull(idColumn);
            _idColumn = idColumn;
        }
        public String getContextPathColumn()
        {
            return _contextPathColumn;
        }
        public void setContextPathColumn(String contextPathColumn)
        {
            checkNotNull(contextPathColumn);
            _contextPathColumn = contextPathColumn;
        }
        public String getVirtualHostColumn()
        {
            return _virtualHostColumn;
        }
        public void setVirtualHostColumn(String virtualHostColumn)
        {
            checkNotNull(virtualHostColumn);
            _virtualHostColumn = virtualHostColumn;
        }
        public String getLastNodeColumn()
        {
            return _lastNodeColumn;
        }
        public void setLastNodeColumn(String lastNodeColumn)
        {
            checkNotNull(lastNodeColumn);
            _lastNodeColumn = lastNodeColumn;
        }
        public String getAccessTimeColumn()
        {
            return _accessTimeColumn;
        }
        public void setAccessTimeColumn(String accessTimeColumn)
        {
            checkNotNull(accessTimeColumn);
            _accessTimeColumn = accessTimeColumn;
        }
        public String getLastAccessTimeColumn()
        {
            return _lastAccessTimeColumn;
        }
        public void setLastAccessTimeColumn(String lastAccessTimeColumn)
        {
            checkNotNull(lastAccessTimeColumn);
            _lastAccessTimeColumn = lastAccessTimeColumn;
        }
        public String getCreateTimeColumn()
        {
            return _createTimeColumn;
        }
        public void setCreateTimeColumn(String createTimeColumn)
        {
            checkNotNull(createTimeColumn);
            _createTimeColumn = createTimeColumn;
        }
        public String getCookieTimeColumn()
        {
            return _cookieTimeColumn;
        }
        public void setCookieTimeColumn(String cookieTimeColumn)
        {
            checkNotNull(cookieTimeColumn);
            _cookieTimeColumn = cookieTimeColumn;
        }
        public String getLastSavedTimeColumn()
        {
            return _lastSavedTimeColumn;
        }
        public void setLastSavedTimeColumn(String lastSavedTimeColumn)
        {
            checkNotNull(lastSavedTimeColumn);
            _lastSavedTimeColumn = lastSavedTimeColumn;
        }
        public String getExpiryTimeColumn()
        {
            return _expiryTimeColumn;
        }
        public void setExpiryTimeColumn(String expiryTimeColumn)
        {
            checkNotNull(expiryTimeColumn);
            _expiryTimeColumn = expiryTimeColumn;
        }
        public String getMaxIntervalColumn()
        {
            return _maxIntervalColumn;
        }
        public void setMaxIntervalColumn(String maxIntervalColumn)
        {
            checkNotNull(maxIntervalColumn);
            _maxIntervalColumn = maxIntervalColumn;
        }
        public String getMapColumn()
        {
            return _mapColumn;
        }
        public void setMapColumn(String mapColumn)
        {
            checkNotNull(mapColumn);
            _mapColumn = mapColumn;
        }
        
        public String getCreateStatementAsString ()
        {
            if (_dbAdaptor == null)
                throw new IllegalStateException ("No DBAdaptor");
            
            String blobType = _dbAdaptor.getBlobType();
            String longType = _dbAdaptor.getLongType();
            
            return "create table "+_tableName+" ("+_idColumn+" varchar(120), "+
                    _contextPathColumn+" varchar(60), "+_virtualHostColumn+" varchar(60), "+_lastNodeColumn+" varchar(60), "+_accessTimeColumn+" "+longType+", "+
                    _lastAccessTimeColumn+" "+longType+", "+_createTimeColumn+" "+longType+", "+_cookieTimeColumn+" "+longType+", "+
                    _lastSavedTimeColumn+" "+longType+", "+_expiryTimeColumn+" "+longType+", "+_maxIntervalColumn+" "+longType+", "+
                    _mapColumn+" "+blobType+", primary key("+_idColumn+", "+_contextPathColumn+","+_virtualHostColumn+"))";
        }
        
        public String getCreateIndexOverExpiryStatementAsString (String indexName)
        {
            return "create index "+indexName+" on "+getTableName()+" ("+getExpiryTimeColumn()+")";
        }
        
        public String getCreateIndexOverSessionStatementAsString (String indexName)
        {
            return "create index "+indexName+" on "+getTableName()+" ("+getIdColumn()+", "+getContextPathColumn()+")";
        }
        
        public String getAlterTableForMaxIntervalAsString ()
        {
            if (_dbAdaptor == null)
                throw new IllegalStateException ("No DBAdaptor");
            String longType = _dbAdaptor.getLongType();
            String stem = "alter table "+getTableName()+" add "+getMaxIntervalColumn()+" "+longType;
            if (_dbAdaptor.getDBName().contains("oracle"))
                return stem + " default "+ MAX_INTERVAL_NOT_SET + " not null";
            else
                return stem +" not null default "+ MAX_INTERVAL_NOT_SET;
        }
        
        private void checkNotNull(String s)
        {
            if (s == null)
                throw new IllegalArgumentException(s);
        }
        public String getInsertSessionStatementAsString()
        {
           return "insert into "+getTableName()+
            " ("+getIdColumn()+", "+getContextPathColumn()+", "+getVirtualHostColumn()+", "+getLastNodeColumn()+
            ", "+getAccessTimeColumn()+", "+getLastAccessTimeColumn()+", "+getCreateTimeColumn()+", "+getCookieTimeColumn()+
            ", "+getLastSavedTimeColumn()+", "+getExpiryTimeColumn()+", "+getMaxIntervalColumn()+", "+getMapColumn()+") "+
            " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        }

        public PreparedStatement getUpdateSessionStatement(Connection connection, String canonicalContextPath)
                throws SQLException
        {
            String s =  "update "+getTableName()+
                    " set "+getLastNodeColumn()+" = ?, "+getAccessTimeColumn()+" = ?, "+
                    getLastAccessTimeColumn()+" = ?, "+getLastSavedTimeColumn()+" = ?, "+getExpiryTimeColumn()+" = ?, "+
                    getMaxIntervalColumn()+" = ?, "+getMapColumn()+" = ? where ";

            if (canonicalContextPath == null || "".equals(canonicalContextPath))
            {
                if (_dbAdaptor.isEmptyStringNull())
                {
                    s = s+getIdColumn()+" = ? and "+
                            getContextPathColumn()+" is null and "+
                            getVirtualHostColumn()+" = ?";
                    return connection.prepareStatement(s);
                }
            }

            return connection.prepareStatement(s+getIdColumn()+" = ? and "+getContextPathColumn()+
                                               " = ? and "+getVirtualHostColumn()+" = ?");
        }

      
        public PreparedStatement getMyExpiredSessionsStatement (Connection connection, String canonicalContextPath, String vhost, long expiry)
        throws SQLException
        {
            if (_dbAdaptor == null)
                throw new IllegalStateException("No DB adaptor");


            if (canonicalContextPath == null || "".equals(canonicalContextPath))
            {
                if (_dbAdaptor.isEmptyStringNull())
                {
                    PreparedStatement statement = connection.prepareStatement("select "+getIdColumn()+", "+getExpiryTimeColumn()+
                                                                              " from "+getTableName()+" where "+
                                                                              getContextPathColumn()+" is null and "+
                                                                              getVirtualHostColumn()+" = ? and "+getExpiryTimeColumn()+" >0 and "+getExpiryTimeColumn()+" <= ?");
                    statement.setString(1, vhost);
                    statement.setLong(2, expiry);
                    return statement;
                }
            }

            PreparedStatement statement = connection.prepareStatement("select "+getIdColumn()+", "+getExpiryTimeColumn()+
                                                                      " from "+getTableName()+" where "+getContextPathColumn()+" = ? and "+
                                                                      getVirtualHostColumn()+" = ? and "+
                                                                      getExpiryTimeColumn()+" >0 and "+getExpiryTimeColumn()+" <= ?");

            statement.setString(1, canonicalContextPath);
            statement.setString(2, vhost);
            statement.setLong(3, expiry);
            return statement;
        }
      
    
        
        public PreparedStatement getAllAncientExpiredSessionsStatement (Connection connection)
        throws SQLException
        {
            if (_dbAdaptor == null)
                throw new IllegalStateException("No DB adaptor");

            PreparedStatement statement = connection.prepareStatement("select "+getIdColumn()+", "+getContextPathColumn()+", "+getVirtualHostColumn()+
                                                                      " from "+getTableName()+
                                                                      " where "+getExpiryTimeColumn()+" >0 and "+getExpiryTimeColumn()+" <= ?");
            return statement;
        }
     
     
        public PreparedStatement getCheckSessionExistsStatement (Connection connection, String canonicalContextPath)
        throws SQLException
        {
            if (_dbAdaptor == null)
                throw new IllegalStateException("No DB adaptor");


            if (canonicalContextPath == null || "".equals(canonicalContextPath))
            {
                if (_dbAdaptor.isEmptyStringNull())
                {
                    PreparedStatement statement = connection.prepareStatement("select "+getIdColumn()+", "+getExpiryTimeColumn()+
                                                                              " from "+getTableName()+
                                                                              " where "+getIdColumn()+" = ? and "+
                                                                              getContextPathColumn()+" is null and "+
                                                                              getVirtualHostColumn()+" = ?");
                    return statement;
                }
            }

            PreparedStatement statement = connection.prepareStatement("select "+getIdColumn()+", "+getExpiryTimeColumn()+
                                                                      " from "+getTableName()+
                                                                      " where "+getIdColumn()+" = ? and "+
                                                                      getContextPathColumn()+" = ? and "+
                                                                      getVirtualHostColumn()+" = ?");
            return statement;
        }

        public void fillCheckSessionExistsStatement (PreparedStatement statement, String id, SessionContext contextId)
        throws SQLException
        {
            statement.clearParameters();
            ParameterMetaData metaData = statement.getParameterMetaData();
            if (metaData.getParameterCount() < 3)
            {
                statement.setString(1, id);
                statement.setString(2, contextId.getVhost());
            }
            else
            {
                statement.setString(1, id);
                statement.setString(2, contextId.getCanonicalContextPath());
                statement.setString(3, contextId.getVhost());
            }
        }
        
        
        public PreparedStatement getLoadStatement (Connection connection, String id, SessionContext contextId)
        throws SQLException
        { 
            if (_dbAdaptor == null)
                throw new IllegalStateException("No DB adaptor");


            if (contextId.getCanonicalContextPath() == null || "".equals(contextId.getCanonicalContextPath()))
            {
                if (_dbAdaptor.isEmptyStringNull())
                {
                    PreparedStatement statement = connection.prepareStatement("select * from "+getTableName()+
                                                                              " where "+getIdColumn()+" = ? and "+
                                                                              getContextPathColumn()+" is null and "+
                                                                              getVirtualHostColumn()+" = ?");
                    statement.setString(1, id);
                    statement.setString(2, contextId.getVhost());

                    return statement;
                }
            }

            PreparedStatement statement = connection.prepareStatement("select * from "+getTableName()+
                                                                      " where "+getIdColumn()+" = ? and "+getContextPathColumn()+
                                                                      " = ? and "+getVirtualHostColumn()+" = ?");
            statement.setString(1, id);
            statement.setString(2, contextId.getCanonicalContextPath());
            statement.setString(3, contextId.getVhost());

            return statement;
        }

        
        
        public PreparedStatement getUpdateStatement (Connection connection, String id, SessionContext contextId)
        throws SQLException
        {
            if (_dbAdaptor == null)
                throw new IllegalStateException("No DB adaptor");

            String s = "update "+getTableName()+
                    " set "+getLastNodeColumn()+" = ?, "+getAccessTimeColumn()+" = ?, "+
                    getLastAccessTimeColumn()+" = ?, "+getLastSavedTimeColumn()+" = ?, "+getExpiryTimeColumn()+" = ?, "+
                    getMaxIntervalColumn()+" = ?, "+getMapColumn()+" = ? where ";

            if (contextId.getCanonicalContextPath() == null || "".equals(contextId.getCanonicalContextPath()))
            {
                if (_dbAdaptor.isEmptyStringNull())
                {
                    PreparedStatement statement = connection.prepareStatement(s+getIdColumn()+" = ? and "+
                            getContextPathColumn()+" is null and "+
                            getVirtualHostColumn()+" = ?");
                    statement.setString(1, id);
                    statement.setString(2, contextId.getVhost());
                    return statement;
                }
            }
            PreparedStatement statement = connection.prepareStatement(s+getIdColumn()+" = ? and "+getContextPathColumn()+
                                                                      " = ? and "+getVirtualHostColumn()+" = ?");
            statement.setString(1, id);
            statement.setString(2, contextId.getCanonicalContextPath());
            statement.setString(3, contextId.getVhost());

            return statement;
        }
        
        


        public PreparedStatement getDeleteStatement (Connection connection, String id, SessionContext contextId)
        throws Exception
        { 
            if (_dbAdaptor == null)

                throw new IllegalStateException("No DB adaptor");


            if (contextId.getCanonicalContextPath() == null || "".equals(contextId.getCanonicalContextPath()))
            {
                if (_dbAdaptor.isEmptyStringNull())
                {
                    PreparedStatement statement = connection.prepareStatement("delete from "+getTableName()+
                                                                              " where "+getIdColumn()+" = ? and "+getContextPathColumn()+
                                                                              " = ? and "+getVirtualHostColumn()+" = ?");
                    statement.setString(1, id);
                    statement.setString(2, contextId.getVhost());
                    return statement;
                }
            }

            PreparedStatement statement = connection.prepareStatement("delete from "+getTableName()+
                                                                      " where "+getIdColumn()+" = ? and "+getContextPathColumn()+
                                                                      " = ? and "+getVirtualHostColumn()+" = ?");
            statement.setString(1, id);
            statement.setString(2, contextId.getCanonicalContextPath());
            statement.setString(3, contextId.getVhost());

            return statement;

        }

        
        /**
         * Set up the tables in the database
         * @throws SQLException
         */
        /**
         * @throws SQLException
         */
        public void prepareTables()
        throws SQLException
        {
            try (Connection connection = _dbAdaptor.getConnection();
                 Statement statement = connection.createStatement())
            {
                //make the id table
                connection.setAutoCommit(true);
                DatabaseMetaData metaData = connection.getMetaData();
                _dbAdaptor.adaptTo(metaData);
                    
                
                //make the session table if necessary
                String tableName = _dbAdaptor.convertIdentifier(getTableName());
                try (ResultSet result = metaData.getTables(null, null, tableName, null))
                {
                    if (!result.next())
                    {
                        //table does not exist, so create it
                        statement.executeUpdate(getCreateStatementAsString());
                    }
                    else
                    {
                        //session table exists, check it has maxinterval column
                        ResultSet colResult = null;
                        try
                        {
                            colResult = metaData.getColumns(null, null,
                                                            _dbAdaptor.convertIdentifier(getTableName()), 
                                                            _dbAdaptor.convertIdentifier(getMaxIntervalColumn()));
                        }
                        catch (SQLException s)
                        {
                            LOG.warn("Problem checking if "+getTableName()+
                                     " table contains "+getMaxIntervalColumn()+" column. Ensure table contains column definition: \""
                                    + getMaxIntervalColumn()+" long not null default -999\"");
                            throw s;
                        }
                        try
                        {
                            if (!colResult.next())
                            {
                                try
                                {
                                    //add the maxinterval column
                                    statement.executeUpdate(getAlterTableForMaxIntervalAsString());
                                }
                                catch (SQLException s)
                                {
                                    LOG.warn("Problem adding "+getMaxIntervalColumn()+
                                             " column. Ensure table contains column definition: \""+getMaxIntervalColumn()+
                                             " long not null default -999\"");
                                    throw s;
                                }
                            }
                        }
                        finally
                        {
                            colResult.close();
                        }
                    }
                }
                //make some indexes on the JettySessions table
                String index1 = "idx_"+getTableName()+"_expiry";
                String index2 = "idx_"+getTableName()+"_session";

                boolean index1Exists = false;
                boolean index2Exists = false;
                try (ResultSet result = metaData.getIndexInfo(null, null, tableName, false, false))
                {
                    while (result.next())
                    {
                        String idxName = result.getString("INDEX_NAME");
                        if (index1.equalsIgnoreCase(idxName))
                            index1Exists = true;
                        else if (index2.equalsIgnoreCase(idxName))
                            index2Exists = true;
                    }
                }
                if (!index1Exists)
                    statement.executeUpdate(getCreateIndexOverExpiryStatementAsString(index1));
                if (!index2Exists)
                    statement.executeUpdate(getCreateIndexOverSessionStatementAsString(index2));
            }
        }
    }
    
    
   
  
    public JDBCSessionDataStore ()
    {
        super ();
    }

  



    @Override
    protected void doStart() throws Exception
    {         
        if (_dbAdaptor == null)
            throw new IllegalStateException("No jdbc config");
        
        _unloadables.clear();
        initialize();
        super.doStart();
    }




    @Override
    protected void doStop() throws Exception
    {
        _unloadables.clear();
        super.doStop();
    }




    public void initialize () throws Exception
    {
        if (!_initialized)
        {
            _initialized = true;
   
            //taking the defaults if one not set
            if (_sessionTableSchema == null)
                _sessionTableSchema = new SessionTableSchema();
            
            _dbAdaptor.initialize();
            _sessionTableSchema.setDatabaseAdaptor(_dbAdaptor);
            _sessionTableSchema.prepareTables();
        }
    }


 
    /** 
     * @see org.eclipse.jetty.server.session.SessionDataStore#load(java.lang.String)
     */
    @Override
    public SessionData load(String id) throws Exception
    {
        if (getLoadAttempts() > 0 && loadAttemptsExhausted(id))
            throw new UnreadableSessionDataException(id, _context, true);
            
        final AtomicReference<SessionData> reference = new AtomicReference<SessionData>();
        final AtomicReference<Exception> exception = new AtomicReference<Exception>();
        
        Runnable r = new Runnable()
        {
            public void run ()
            {
                try (Connection connection = _dbAdaptor.getConnection();
                     PreparedStatement statement = _sessionTableSchema.getLoadStatement(connection, id, _context);
                     ResultSet result = statement.executeQuery())
                {
                    SessionData data = null;
                    if (result.next())
                    {                    
                        data = newSessionData(id,
                                              result.getLong(_sessionTableSchema.getCreateTimeColumn()), 
                                              result.getLong(_sessionTableSchema.getAccessTimeColumn()), 
                                              result.getLong(_sessionTableSchema.getLastAccessTimeColumn()), 
                                              result.getLong(_sessionTableSchema.getMaxIntervalColumn()));
                        data.setCookieSet(result.getLong(_sessionTableSchema.getCookieTimeColumn()));
                        data.setLastNode(result.getString(_sessionTableSchema.getLastNodeColumn()));
                        data.setLastSaved(result.getLong(_sessionTableSchema.getLastSavedTimeColumn()));
                        data.setExpiry(result.getLong(_sessionTableSchema.getExpiryTimeColumn()));
                        data.setContextPath(result.getString(_sessionTableSchema.getContextPathColumn())); //TODO needed? this is part of the key now
                        data.setVhost(result.getString(_sessionTableSchema.getVirtualHostColumn())); //TODO needed??? this is part of the key now

                        try (InputStream is = _dbAdaptor.getBlobInputStream(result, _sessionTableSchema.getMapColumn());
                             ClassLoadingObjectInputStream ois = new ClassLoadingObjectInputStream(is))
                        {
                            Object o = ois.readObject();
                            data.putAllAttributes((Map<String,Object>)o);
                        }
                        catch (Exception e)
                        {
                            if (getLoadAttempts() > 0)
                            {
                                incLoadAttempt (id);
                            }
                            throw new UnreadableSessionDataException (id, _context, e);
                        }
                        
                        //if the session successfully loaded, remove failed attempts
                        _unloadables.remove(id);
                        
                        if (LOG.isDebugEnabled())
                            LOG.debug("LOADED session {}", data);
                    }
                    else
                        if (LOG.isDebugEnabled())
                            LOG.debug("No session {}", id);
                    
                    reference.set(data);
                }
                catch (UnreadableSessionDataException e)
                {
                    if (getLoadAttempts() > 0 && loadAttemptsExhausted(id) && isDeleteUnloadableSessions())
                    {
                        try
                        {
                            delete (id);
                            _unloadables.remove(id);
                        }
                        catch (Exception x)
                        {
                            LOG.warn("Problem deleting unloadable session {}", id);
                        }

                    }
                    exception.set(e);
                }
                catch (Exception e)
                {
                    exception.set(e);
                }
            }
        };

        //ensure this runs with context classloader set
        _context.run(r);

        if (exception.get() != null)
            throw exception.get();

        return reference.get();
    }



    /** 
     * @see org.eclipse.jetty.server.session.SessionDataStore#delete(java.lang.String)
     */
    @Override
    public boolean delete(String id) throws Exception
    {
        try (Connection connection = _dbAdaptor.getConnection();
             PreparedStatement statement = _sessionTableSchema.getDeleteStatement(connection, id, _context))
        {
            connection.setAutoCommit(true);
            int rows = statement.executeUpdate();
            if (LOG.isDebugEnabled())
                LOG.debug("Deleted Session {}:{}",id,(rows>0));
            
            return rows > 0;
        }
    }




    /** 
     * @see org.eclipse.jetty.server.session.AbstractSessionDataStore#doStore(java.lang.String, org.eclipse.jetty.server.session.SessionData, boolean)
     */
    @Override
    public void doStore(String id, SessionData data, boolean isNew) throws Exception
    {
        if (data==null || id==null)
            return;

        if (isNew)
        {     
            doInsert(id, data);
        }
        else
        {
            doUpdate(id, data);            
        }
    }


    private void doInsert (String id, SessionData data) 
    throws Exception
    {
        String s = _sessionTableSchema.getInsertSessionStatementAsString();


        try (Connection connection = _dbAdaptor.getConnection())        
        {
            connection.setAutoCommit(true);
            try  (PreparedStatement statement = connection.prepareStatement(s))
            {
                statement.setString(1, id); //session id
                statement.setString(2, _context.getCanonicalContextPath()); //context path
                statement.setString(3, _context.getVhost()); //first vhost
                statement.setString(4, data.getLastNode());//my node id
                statement.setLong(5, data.getAccessed());//accessTime
                statement.setLong(6, data.getLastAccessed()); //lastAccessTime
                statement.setLong(7, data.getCreated()); //time created
                statement.setLong(8, data.getCookieSet());//time cookie was set
                statement.setLong(9, data.getLastSaved()); //last saved time
                statement.setLong(10, data.getExpiry());
                statement.setLong(11, data.getMaxInactiveMs());

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(baos);
                oos.writeObject(data.getAllAttributes());
                oos.flush();
                byte[] bytes = baos.toByteArray();

                ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                statement.setBinaryStream(12, bais, bytes.length);//attribute map as blob
                statement.executeUpdate();
                if (LOG.isDebugEnabled())
                    LOG.debug("Inserted session "+data);
            }
        }
    }

    private void doUpdate (String id, SessionData data)
            throws Exception
    {
        try (Connection connection = _dbAdaptor.getConnection())        
        {
            connection.setAutoCommit(true);
            try (PreparedStatement statement = _sessionTableSchema.getUpdateSessionStatement(connection, _context.getCanonicalContextPath()))
            {
                statement.setString(1, data.getLastNode());//should be my node id
                statement.setLong(2, data.getAccessed());//accessTime
                statement.setLong(3, data.getLastAccessed()); //lastAccessTime
                statement.setLong(4, data.getLastSaved()); //last saved time
                statement.setLong(5, data.getExpiry());
                statement.setLong(6, data.getMaxInactiveMs());

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(baos);
                oos.writeObject(data.getAllAttributes());
                oos.flush();
                byte[] bytes = baos.toByteArray();
                ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                statement.setBinaryStream(7, bais, bytes.length);//attribute map as blob

                if ((_context.getCanonicalContextPath() == null || "".equals(_context.getCanonicalContextPath())) && _dbAdaptor.isEmptyStringNull())
                {
                    statement.setString(8, id);
                    statement.setString(9, _context.getVhost()); 
                }
                else
                {
                    statement.setString(8, id);
                    statement.setString(9, _context.getCanonicalContextPath());
                    statement.setString(10, _context.getVhost());
                }

                statement.executeUpdate();

                if (LOG.isDebugEnabled())
                    LOG.debug("Updated session "+data);
            }
        }
    }



    /** 
     * @see org.eclipse.jetty.server.session.SessionDataStore#getExpired(java.util.Set)
     */
    @Override
    public Set<String> getExpired(Set<String> candidates)
    {
        if (LOG.isDebugEnabled())
            LOG.debug("Getting expired sessions "+System.currentTimeMillis());

        long now = System.currentTimeMillis();
        
        
        Set<String> expiredSessionKeys = new HashSet<>();
        try (Connection connection = _dbAdaptor.getConnection())
        {
            connection.setAutoCommit(true);
            
            /*
             * 1. Select sessions for our context that have expired
             */
            long upperBound = now;
            if (LOG.isDebugEnabled())
                LOG.debug ("{}- Pass 1: Searching for sessions for context {} expired before {}", _context.getWorkerName(), _context.getCanonicalContextPath(), upperBound);

            try (PreparedStatement statement = _sessionTableSchema.getMyExpiredSessionsStatement(connection, _context.getCanonicalContextPath(), _context.getVhost(), upperBound))
            {
                try (ResultSet result = statement.executeQuery())
                {
                    while (result.next())
                    {
                        String sessionId = result.getString(_sessionTableSchema.getIdColumn());
                        long exp = result.getLong(_sessionTableSchema.getExpiryTimeColumn());
                        expiredSessionKeys.add(sessionId);
                        if (LOG.isDebugEnabled()) LOG.debug (_context.getCanonicalContextPath()+"- Found expired sessionId="+sessionId);
                    }
                }
            }

            /*
             *  2. Select sessions for any node or context that have expired a long time ago (ie at least 3 grace periods ago)
             */
            try (PreparedStatement selectExpiredSessions = _sessionTableSchema.getAllAncientExpiredSessionsStatement(connection))
            {
                upperBound = now - (3 * _gracePeriodMs);
                if (upperBound > 0)
                {
                    if (LOG.isDebugEnabled()) LOG.debug("{}- Pass 2: Searching for sessions expired before {}",_context.getWorkerName(), upperBound);

                    selectExpiredSessions.setLong(1, upperBound);
                    try (ResultSet result = selectExpiredSessions.executeQuery())
                    {
                        while (result.next())
                        {
                            String sessionId = result.getString(_sessionTableSchema.getIdColumn());
                            String ctxtpth = result.getString(_sessionTableSchema.getContextPathColumn());
                            String vh = result.getString(_sessionTableSchema.getVirtualHostColumn());
                            expiredSessionKeys.add(sessionId);
                            if (LOG.isDebugEnabled()) LOG.debug ("{}- Found expired sessionId=",_context.getWorkerName(), sessionId);
                        }
                    }
                }
            }
            

            Set<String> notExpiredInDB = new HashSet<>();
            for (String k: candidates)
            {
                //there are some keys that the session store thought had expired, but were not
                //found in our sweep either because it is no longer in the db, or its
                //expiry time was updated
                if (!expiredSessionKeys.contains(k))
                    notExpiredInDB.add(k);
            }


            if (!notExpiredInDB.isEmpty())
            {
                //we have some sessions to check
                try (PreparedStatement checkSessionExists = _sessionTableSchema.getCheckSessionExistsStatement(connection, _context.getCanonicalContextPath()))
                {
                    for (String k: notExpiredInDB)
                    {
                        _sessionTableSchema.fillCheckSessionExistsStatement (checkSessionExists, k, _context);
                        try (ResultSet result = checkSessionExists.executeQuery())
                        {        
                            if (!result.next())
                            {
                                //session doesn't exist any more, can be expired
                                expiredSessionKeys.add(k);
                            }
                            //else its expiry time has not been reached
                        }
                        catch (Exception e)
                        {
                            LOG.warn("Problem checking if potentially expired session {} exists in db", k,e);
                        }
                    }

                }
            }

            return expiredSessionKeys;
        }
        catch (Exception e)
        {
            LOG.warn(e);
            return expiredSessionKeys; //return whatever we got
        } 
       
    }
    public int getGracePeriodSec ()
    {
        return (int)(_gracePeriodMs == 0L? 0 : _gracePeriodMs/1000L);
    }
    
    public void setGracePeriodSec (int sec)
    {
        if (sec < 0)
            _gracePeriodMs = 0;
        else
            _gracePeriodMs = sec * 1000L;
    }
    
    public void setDatabaseAdaptor (DatabaseAdaptor dbAdaptor)
    {
        checkStarted();
        _dbAdaptor = dbAdaptor;
    }
    
    public void setSessionTableSchema (SessionTableSchema schema)
    {
        checkStarted();
        _sessionTableSchema = schema;
    }

    public void setLoadAttempts (int attempts)
    {
        checkStarted();
        _attempts = attempts;
    }

    public int getLoadAttempts ()
    {
        return _attempts;
    }
    
    public boolean loadAttemptsExhausted (String id)
    {
        AtomicInteger i = _unloadables.get(id);
        if (i == null)
            return false;
        return (i.get() >= _attempts);
    }
    
    public void setDeleteUnloadableSessions (boolean delete)
    {
        checkStarted();
        _deleteUnloadables = delete;
    }
    
    public boolean isDeleteUnloadableSessions ()
    {
        return _deleteUnloadables;
    }
    
    
    protected void incLoadAttempt (String id)
    {
        AtomicInteger i = new AtomicInteger(0);
        AtomicInteger count = _unloadables.putIfAbsent(id, i);
        if (count == null)
            count = i;
        count.incrementAndGet();
    }
    
  
    
    public int getLoadAttempts (String id)
    {
        AtomicInteger i = _unloadables.get(id);
        if (i == null)
            return 0;
        return i.get();
    }
    
    public Set<String> getUnloadableSessions ()
    {
        return new HashSet<String>(_unloadables.keySet());
    }
    
   public void clearUnloadableSessions()
   {
       _unloadables.clear();
   }





   /** 
    * @see org.eclipse.jetty.server.session.SessionDataStore#isPassivating()
    */
   @Override
   public boolean isPassivating()
   {
       return true;
   }
}








