package org.randomcoder.proxy.support;

import org.apache.log4j.Logger;
import org.randomcoder.proxy.support.EndpointEvent.EventType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Endpoint tracker which watches Endpoint instances and cleans them up after a
 * specified period of inactivity.
 */
public class EndpointTracker {
  /**
   * Logger instance.
   */
  protected static final Logger logger =
      Logger.getLogger(EndpointTracker.class);

  /**
   * Map of ids to endpoints.
   */
  protected final ConcurrentHashMap<String, Endpoint> endpointMap =
      new ConcurrentHashMap<String, Endpoint>();

  /**
   * Map of ids to expiration times.
   */
  protected final ConcurrentHashMap<String, Long> expirationMap =
      new ConcurrentHashMap<String, Long>();

  /**
   * List of events.
   */
  protected final ConcurrentLinkedQueue<EndpointEvent> events =
      new ConcurrentLinkedQueue<EndpointEvent>();

  /**
   * Maximum idle time in milliseconds;
   */
  protected final long maxIdle;

  /**
   * Time to sleep between eviction runs in milliseconds.
   */
  protected final long evictionFrequency;

  private final ReaperThread reaperThread;

  /**
   * Creats a new endpoint tracker using default values.
   */
  public EndpointTracker() {
    this(60000L, 10000L);
  }

  /**
   * Creates a new endpoint tracker.
   *
   * @param maxIdle           maximum time before idle threads are killed (in milliseconds)
   * @param evictionFrequency how often to perform evictions
   */
  public EndpointTracker(long maxIdle, long evictionFrequency) {
    this.maxIdle = maxIdle;
    this.evictionFrequency = evictionFrequency;
    reaperThread = new ReaperThread();
    reaperThread.start();
    logger.info("Endpoint tracker initialized");
  }

  /**
   * Destroys the tracker.
   */
  public void destroy() {
    logger.info("Endpoint tracker shutting down...");

    reaperThread.shutdown();
    try {
      reaperThread.join(30000);
    } catch (InterruptedException ignored) {
    }

    int count = 0;

    // make sure all referenced connections are closed
    for (Map.Entry<String, Endpoint> entry : endpointMap.entrySet()) {
      String id = entry.getKey();
      expirationMap.remove(id);
      Endpoint endpoint = endpointMap.remove(id);
      if (endpoint != null) {
        try {
          endpoint.close();
        } catch (Throwable ignored) {
        }
        count++;
      }
    }

    logger.info("Endpoint tracker shutdown, " + count + " endpoints closed");
  }

  /**
   * Adds a new endpoint to the tracker.
   *
   * @param endpoint endpoint to add
   * @return unique identifier
   */
  public String add(Endpoint endpoint) {
    String id = UUID.randomUUID().toString();

    events.offer(new EndpointEvent(id, endpoint.toString(), EventType.CONNECT,
        System.currentTimeMillis()));

    endpointMap.put(id, endpoint);
    expirationMap.put(id, System.currentTimeMillis() + maxIdle);

    return id;
  }

  /**
   * Removes an existing endpoint from the tracker.
   *
   * @param id unique identifier of endpoint to remove
   */
  public void remove(String id) {
    expirationMap.remove(id);
    Endpoint endpoint = endpointMap.remove(id);
    events.offer(
        new EndpointEvent(id, endpoint == null ? "null" : endpoint.toString(),
            EventType.DISCONNECT, System.currentTimeMillis()));
    try {
      if (endpoint != null) {
        endpoint.close();
      }
    } catch (Throwable ignored) {
    }
  }

  /**
   * Signals the completion of a receive() call.
   *
   * @param id unique identifier of endpoint
   */
  public void receiveComplete(String id) {
    Endpoint endpoint = endpointMap.get(id);
    events.offer(
        new EndpointEvent(id, endpoint == null ? "null" : endpoint.toString(),
            EventType.RECEIVE_COMPLETE, System.currentTimeMillis()));
  }

  /**
   * Signals an error on a receive() call.
   *
   * @param id unique identifier of endpoint
   */
  public void receiveError(String id) {
    Endpoint endpoint = endpointMap.get(id);
    events.offer(
        new EndpointEvent(id, endpoint == null ? "null" : endpoint.toString(),
            EventType.RECEIVE_ERROR, System.currentTimeMillis()));
  }

  /**
   * Refreshes an endpoint's timeout value, typically in response to activity
   * or a keep-alive request.
   *
   * @param id unique identifier of endpoint to refresh
   * @return <code>true</code> if endpoint was still active
   */
  public boolean refresh(String id) {
    long timeout = System.currentTimeMillis() + maxIdle;
    Long oldTimeout = expirationMap.replace(id, timeout);

    logger.debug("Refresh [" + id + "]: old=" + oldTimeout + ",new=" + timeout);

    return (oldTimeout != null);
  }

  /**
   * Gets an endpoint by id.
   *
   * @param id unique identifier of endpoint to retrieve
   * @return endpoint, or <code>null</code> if not found
   */
  public Endpoint getEndpoint(String id) {
    return endpointMap.get(id);
  }

  /**
   * Gets the endpoint map (for status).
   *
   * @return endpoint map
   */
  public TreeMap<String, Endpoint> getEndpointMap() {
    return new TreeMap<String, Endpoint>(endpointMap);
  }

  /**
   * Gets the expiration map (for status).
   *
   * @return expiration map
   */
  public Map<String, Long> getExpirationMap() {
    return new TreeMap<String, Long>(expirationMap);
  }

  /**
   * Gets the list of recent events.
   *
   * @return event list
   */
  public List<EndpointEvent> getEvents() {
    List<EndpointEvent> result = new ArrayList<EndpointEvent>(events);
    Collections.reverse(result);
    return result;
  }

  private final class ReaperThread extends Thread {
    private volatile boolean shutdown = false;

    /**
     * Creates a new reaper thread.
     */
    public ReaperThread() {
      super("Endpoint reaper");
    }

    @Override public void run() {
      while (!shutdown) {
        try {
          long now = System.currentTimeMillis();

          logger.debug("Checking for stale connections, time = " + now);

          // walk object map
          for (Map.Entry<String, Long> entry : expirationMap.entrySet()) {
            if (entry.getValue() <= now) {
              // remove stale object
              String id = entry.getKey();
              expirationMap.remove(id);
              Endpoint endpoint = endpointMap.remove(id);

              events.offer(new EndpointEvent(id,
                  endpoint == null ? "null" : endpoint.toString(),
                  EventType.EXPIRE, System.currentTimeMillis()));

              if (endpoint != null) {
                logger.info("Closing stale connection with ID " + id);
                try {
                  endpoint.close();
                } catch (Throwable ignored) {
                }
              }
            }
          }

          logger.debug("Done checking for stale connections");

          logger.debug("Clearing event list");

          while (events.size() > 100) {
            events.remove();
          }

          logger.debug("Done clearing event list");

          // sleep until next round
          try {
            Thread.sleep(evictionFrequency);
          } catch (InterruptedException ignored) {
          }
        } catch (Throwable t) {
          // defensive catch to avoid thread death
          logger.error("Caught exception", t);
        }
      }
    }

    /**
     * Requests that this thread be shutdown.
     */
    public void shutdown() {
      shutdown = true;
      interrupt();
    }
  }
}
