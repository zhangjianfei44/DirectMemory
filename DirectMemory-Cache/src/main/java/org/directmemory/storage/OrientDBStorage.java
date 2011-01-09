package org.directmemory.storage;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.directmemory.CacheEntry;

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.db.record.ODatabaseBinary;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;

public class OrientDBStorage extends Storage {
	
	public String baseDir = "orientdb";
	
	ODatabaseBinary db;
	ODatabaseDocumentTx docDb;
	
	private void createDocDb() {
        File base = new File(baseDir + "\\doc");
		if (base.exists()) {
			logger.info("Base folder: " + base.getPath() + " checked ok");
		} else if (base.mkdir()) {
			logger.info("Base folder: " + base.getPath() + " created");
			return;
		} else {
			logger.error("Could not create base directory: " + base.getPath());
		}
		docDb = new ODatabaseDocumentTx("local:" + base.getAbsolutePath() + "\\data");
		docDb.delete();
		docDb.create();
		logger.info("OrientDB document database: " + db.getURL() + " created");
	}
	
	public OrientDBStorage() {
		try {
	        File base = new File(baseDir);
	        if (!base.exists()) {
	        	base.mkdir();
	        }
			createDocDb();
		} catch (Exception e) {
			logger.error("OrientDB database: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	protected void finalize() throws Throwable
	{
		db.close();
		docDb.close();
		super.finalize(); 
	} 
	
	@Override
	protected boolean moveIn(CacheEntry entry) {
		// try to delete it just to be sure 
		try {
			docDb.query(new OSQLSynchQuery<ODocument>("delete * from Entry where key = '" + entry.key + "'"));
		} catch (Exception e1) {
			logger.error("error deleting previous entry with key " + entry.key);
			e1.printStackTrace();
		}
		
		byte [] buffer = entry.bufferData();
		if (buffer == null) {
			// it seems entry was in heap
			try {
				buffer = serializer.serialize(entry.object, entry.object.getClass());
			} catch (IOException e) {
				logger.error("error serializing entry " + entry.key);
				e.printStackTrace();
			}
		} 
		ODocument doc = new ODocument(docDb, "Entry");
		doc.field("buffer", buffer, OType.BINARY);
		doc.field("key", entry.key );
		doc.field("expiresOn" , entry.expiresOn);
		doc.field("clazz" , entry.clazz());
		doc.save();

		logger.debug("succesfully stored entry " + entry.key + " to database " + db.getURL());
		
		entry.array = null; // just to be sure
		entry.object = null;
		entry.size = buffer.length;

		return true;
	}

	@Override
	public boolean moveToHeap(CacheEntry entry) {		
		try {
			List<ODocument> result = docDb.query(
					  new OSQLSynchQuery<ODocument>("select * from Entry where key = '" + entry.key + "'"));
			if (result.size() == 1) {
				ODocument doc = result.get(0);
				entry.array = doc.field("buffer", OType.BINARY);
				entry.object = serializer.deserialize(entry.array, entry.clazz());
				entry.array = null;
				logger.debug("succesfully restored entry " + entry.key + " from database " + db.getURL());
				doc.delete();
				return true;
			}
		} catch (Exception e) {
			logger.error(e.getMessage());
			e.printStackTrace();
		}
		return false;
	}

	@Override
	public void reset() {
		super.reset();
		db.close();
		db.delete();
		db.open("admin", "admin");
		logger.debug("OrientDB database deleted and created");
	}
	
	@Override
	public CacheEntry delete(String key) {
		CacheEntry entry = entries.get(key);
		if (entry != null) {
			try {
				docDb.query(new OSQLSynchQuery<ODocument>("delete * from Entry where key = '" + entry.key + "'"));
			} catch (Exception e1) {
				logger.error("error deleting previous entry with key " + entry.key);
				e1.printStackTrace();
			}			
			logger.debug("entry " + key + " deleted from the database");
			return super.delete(key);
		} else {
			logger.debug("no entry " + key + " found in the database");
			return null;			
		} 
	}
}