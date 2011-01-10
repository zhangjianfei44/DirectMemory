package org.directmemory.test;


import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.directmemory.cache.CacheManager;
import org.directmemory.measures.Ram;
import org.directmemory.misc.DummyPojo;
import org.directmemory.serialization.ProtoStuffSerializer;
import org.directmemory.supervisor.TimedSupervisor;
import org.directmemory.thread.CacheEnabledThread;
import org.javasimon.Split;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultiThreadedTest {

	private static Logger logger=LoggerFactory.getLogger(MultiThreadedTest.class);
	
	public static CacheManager cache = null;
	public static Split wholeTestSplit = null;
	
	private Random random = new Random();

	private int randomSize() {
		return Ram.Kb((2) + random.nextInt(Ram.Kb(1)));
	}

	@Test
	public void mixedScenario1and1() {

		CacheManager cache = new CacheManager(100, Ram.Mb(5), 1);
		cache.setSerializer(new ProtoStuffSerializer());
//		cache.supervisor = new AsyncBatchSupervisor(750);
		cache.setSupervisor(new TimedSupervisor(1500));
		final Map<String, DummyPojo> items = new ConcurrentHashMap<String, DummyPojo>();
		
		logger.debug("*** begin mixed 1-1");

		ThreadGroup group = new ThreadGroup("test");
		
		int numThreads = 10;
		final int numObjects = 50;
		final int pauseBetweenOps = 10;
		final int numOps = 50;
		
		// reader threads		
		for (int i = 0; i < numThreads; i++) {
			new CacheEnabledThread(group , "test" + i, cache) {
				public void run() {
					int i = 0;
					try {
						sleep((int)(pauseBetweenOps*500)); // give him some time to warmup
						logger.debug("reader started");
						int gots = 0;
						int misseds = 0;
						while (++i < numOps) {
							String key = "test" + random.nextInt(numObjects);
							DummyPojo pojo = (DummyPojo)cache.get(key);
							DummyPojo check = items.get(key);
							logger.debug("check item=" + check);
							if (check != null) {
								if (!check.name.equals(pojo.name)) {
									logger.error("mismatch");
								} else {
									logger.debug("got it");									
								}
								gots++;
							} else {
								misseds++;
							}
							sleep((int)(pauseBetweenOps*3)); 
					    }
						logger.debug("got " + gots + " and missed " + misseds + " over " + numOps + " ops");
					} catch (InterruptedException ex) {
						logger.debug("thread interrupted");
					}
				}
			}.start();
			
			// writer threads		
			new CacheEnabledThread(group , "test" + i, cache) {
				public void run() {
					logger.debug("adder started");
					int i = 0;
					try {
						while (++i < (numOps/2)) {
							String key = "test" + random.nextInt(numObjects);
							DummyPojo pojo = new DummyPojo(key, randomSize());
							cache.put(pojo.name, pojo);
							items.put(pojo.name, pojo);
							logger.debug("should have added item " + key);									
							sleep(pauseBetweenOps); 
					    }
						logger.debug("added " + i + " entries");
					} catch (InterruptedException ex) {
						logger.debug("thread interrupted");
					}
				}
			}.start();
		}
		
		while (group.activeCount() > 0)
			Thread.yield();		
		
		logger.debug(cache.toString());
		CacheManager.displayTimings();
		logger.debug("*** end of mixed 1-1");
	}
	
	@Test
	public void mixedScenario10and1() {

		CacheManager cache = new CacheManager(100, Ram.Mb(2.5), 1);
		cache.setSerializer(new ProtoStuffSerializer());
//		cache.supervisor = new AsyncBatchSupervisor(750);
		cache.setSupervisor(new TimedSupervisor(1500));
		logger.debug("*** begin mixed 1-1");

		ThreadGroup group = new ThreadGroup("test");
		
		int numThreads = 10;
		final int numObjects = 200;
		final int pauseBetweenOps = 19;
		final int numOps = 500;
		
		for (int i = 0; i < numThreads; i++) {
			new CacheEnabledThread(group , "test" + i, cache) {
				public void run() {
					int i = 0;
					try {
//						sleep((int)(pauseBetweenOps*500)); // give him some time to warmup
						logger.debug("reader started");
						int gots = 0;
						int misseds = 0;
						while (++i < numOps) {
							String key = "test" + random.nextInt(numObjects);
							DummyPojo pojo = (DummyPojo)cache.get(key);
							if (pojo != null) {
								org.junit.Assert.assertEquals(key, pojo.name);
								gots++;
							} else {
								misseds++;
							}
							sleep((int)(pauseBetweenOps*2)); 
					    }
						logger.debug("got " + gots + " and missed " + misseds + " over " + numOps + " ops");
					} catch (InterruptedException ex) {
						logger.debug("thread interrupted");
					}
				}
			}.start();
			
		}

		new CacheEnabledThread(group , "test write" , cache) {
			public void run() {
				logger.debug("adder started");
				int i = 0;
				try {
					while (++i < (numOps)) {
						String key = "test" + random.nextInt(numObjects);
						DummyPojo pojo = new DummyPojo(key,randomSize());
						cache.put(pojo.name, pojo);
						sleep(pauseBetweenOps); 
				    }
					logger.debug("added " + i + " entries");
				} catch (InterruptedException ex) {
					logger.debug("thread interrupted");
				}
			}
		}.start();
		
		new CacheEnabledThread(group , "test write2" , cache) {
			public void run() {
				logger.debug("adder started");
				int i = 0;
				try {
					while (++i < (numOps)) {
						String key = "test" + random.nextInt(numObjects);
						DummyPojo pojo = new DummyPojo(key,randomSize());
						cache.put(pojo.name, pojo);
						sleep(pauseBetweenOps); 
				    }
					logger.debug("added " + i + " entries");
				} catch (InterruptedException ex) {
					logger.debug("thread interrupted");
				}
			}
		}.start();
		
		while (group.activeCount() > 0)
			Thread.yield();		
		
		logger.debug(cache.toString());
		CacheManager.displayTimings();
		logger.debug("*** end of mixed 1-1");
	}	
	
//	@AfterClass
//	public static void checkPerformance() {
//		CacheStore.displayTimings();
//		cache.reset();
//	}
}
