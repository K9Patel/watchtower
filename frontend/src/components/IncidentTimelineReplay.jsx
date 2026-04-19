import React, { useEffect, useMemo, useState } from 'react';

const EVENT_COLORS = {
  TOP_CONSUMER_CHANGED: '#38bdf8',
  ANOMALY_STARTED: '#ef4444',
  DEVICE_OFFLINE: '#f59e0b',
};

const EVENT_LABELS = {
  TOP_CONSUMER_CHANGED: 'Top Consumer',
  ANOMALY_STARTED: 'Anomaly',
  DEVICE_OFFLINE: 'Offline',
};

const EVENT_PRIORITY = {
  ANOMALY_STARTED: 3,
  DEVICE_OFFLINE: 2,
  TOP_CONSUMER_CHANGED: 1,
};

const IncidentTimelineReplay = ({ timeline, onReplaySnapshot }) => {
  const snapshots = timeline?.snapshots || [];
  const events = timeline?.events || [];

  const [index, setIndex] = useState(Math.max(0, snapshots.length - 1));

  useEffect(() => {
    // When fresh timeline data arrives, jump to latest point for a live-first default.
    setIndex(Math.max(0, snapshots.length - 1));
  }, [snapshots.length]);

  useEffect(() => {
    const maxIndex = Math.max(0, snapshots.length - 1);
    if (index > maxIndex) {
      setIndex(maxIndex);
    }
  }, [index, snapshots.length]);

  const activeSnapshot = snapshots[index] || null;

  const eventsByMarker = useMemo(() => {
    const map = new Map();
    events.forEach((e) => {
      const marker = String(e.timestamp || '').slice(11, 16);
      if (!map.has(marker)) {
        map.set(marker, []);
      }
      map.get(marker).push(e);
    });
    return map;
  }, [events]);

  const eventDots = useMemo(() => {
    if (!snapshots.length || !events.length) return [];

    const minuteToIndex = new Map();
    snapshots.forEach((snapshot, idx) => {
      minuteToIndex.set(String(snapshot.timestamp || '').slice(0, 16), idx);
    });

    const byIndex = new Map();
    events.forEach((event) => {
      const minuteKey = String(event.timestamp || '').slice(0, 16);
      const snapshotIndex = minuteToIndex.get(minuteKey);
      if (snapshotIndex === undefined) return;

      if (!byIndex.has(snapshotIndex)) {
        byIndex.set(snapshotIndex, []);
      }
      byIndex.get(snapshotIndex).push(event);
    });

    return Array.from(byIndex.entries())
      .map(([dotIndex, dotEvents]) => {
        const types = Array.from(new Set(dotEvents.map((e) => e.type)));
        types.sort((a, b) => (EVENT_PRIORITY[b] || 0) - (EVENT_PRIORITY[a] || 0));
        return {
          index: dotIndex,
          left: (dotIndex / Math.max(1, snapshots.length - 1)) * 100,
          primaryType: types[0],
          events: dotEvents,
          count: dotEvents.length,
        };
      })
      .sort((a, b) => a.index - b.index);
  }, [events, snapshots]);

  const handleChange = (e) => {
    const nextIndex = Number(e.target.value);
    setIndex(nextIndex);
    if (snapshots[nextIndex]) {
      onReplaySnapshot(snapshots[nextIndex]);
    }
  };

  const restoreLive = () => {
    const liveIndex = Math.max(0, snapshots.length - 1);
    setIndex(liveIndex);
    if (snapshots[liveIndex]) {
      onReplaySnapshot(null);
    }
  };

  if (!snapshots.length) {
    return null;
  }

  const markerCount = Math.min(10, snapshots.length);
  const markerStep = Math.max(1, Math.floor((snapshots.length - 1) / Math.max(1, markerCount - 1)));
  const markerIndices = Array.from({ length: markerCount }, (_, i) => Math.min(snapshots.length - 1, i * markerStep));

  return (
    <div className="glass-panel timeline-replay-panel">
      <div className="timeline-replay-header">
        <div>
          <h3>Incident Timeline Replay</h3>
          <p>Drag to replay minute-by-minute state changes across load, top consumer, online count, and incidents.</p>
        </div>
        <button className="timeline-live-btn" onClick={restoreLive}>Back To Live</button>
      </div>

      <div className="timeline-slider-wrap">
        <input
          type="range"
          min="0"
          max={Math.max(0, snapshots.length - 1)}
          value={index}
          onChange={handleChange}
          className="timeline-slider"
        />

        <div className="timeline-event-dots">
          {eventDots.map((dot) => {
            const color = EVENT_COLORS[dot.primaryType] || '#64748b';
            const title = dot.events
              .map((e) => `${EVENT_LABELS[e.type] || e.type}: ${e.message}`)
              .join('\n');
            return (
              <span
                key={`dot-${dot.index}`}
                className={`timeline-event-dot ${dot.index === index ? 'active' : ''}`}
                style={{
                  left: `${dot.left}%`,
                  borderColor: `${color}aa`,
                  background: `${color}22`,
                }}
                title={title}
              >
                <span
                  className="timeline-event-dot-core"
                  style={{ background: color }}
                />
                {dot.count > 1 && <em>{dot.count}</em>}
              </span>
            );
          })}
        </div>

        <div className="timeline-markers">
          {markerIndices.map((i) => {
            const s = snapshots[i];
            return (
              <span key={i} className="timeline-marker" style={{ left: `${(i / Math.max(1, snapshots.length - 1)) * 100}%` }}>
                {s.marker}
              </span>
            );
          })}
        </div>
      </div>

      <div className="timeline-replay-info">
        <div className="timeline-point">
          <span>Replay Time</span>
          <strong>{activeSnapshot?.marker || '--:--'}</strong>
        </div>
        <div className="timeline-point">
          <span>Load</span>
          <strong>{activeSnapshot ? `${activeSnapshot.totalLoadPercent}%` : '--'}</strong>
        </div>
        <div className="timeline-point">
          <span>Top Consumer</span>
          <strong>{activeSnapshot?.topConsumer || 'N/A'}</strong>
        </div>
        <div className="timeline-point">
          <span>Active Devices</span>
          <strong>{activeSnapshot ? `${activeSnapshot.onlineDevices} / ${activeSnapshot.totalDevices}` : '--'}</strong>
        </div>
      </div>

      <div className="timeline-events">
        <h4>Detected Events</h4>
        {eventsByMarker.get(activeSnapshot?.marker || '')?.length ? (
          eventsByMarker.get(activeSnapshot.marker).map((e, idx) => (
            <div key={`${e.type}-${idx}`} className="timeline-event-item">
              <span
                className="timeline-event-tag"
                style={{
                  background: `${EVENT_COLORS[e.type] || '#64748b'}22`,
                  color: EVENT_COLORS[e.type] || '#94a3b8',
                  borderColor: `${EVENT_COLORS[e.type] || '#64748b'}55`,
                }}
              >
                {EVENT_LABELS[e.type] || e.type}
              </span>
              <p>{e.message}</p>
            </div>
          ))
        ) : (
          <p className="timeline-event-empty">No major events on this minute.</p>
        )}
      </div>
    </div>
  );
};

export default IncidentTimelineReplay;
