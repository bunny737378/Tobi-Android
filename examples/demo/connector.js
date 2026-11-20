const connectorId = "tobi.demo";

const demoEntry = {
  uid: "big-buck-bunny",
  name: "Big Buck Bunny",
  kind: "movie",
  visuals: {
    cover: "https://peach.blender.org/wp-content/uploads/title_anouncement.jpg",
    banner: "https://peach.blender.org/wp-content/uploads/bbb-splash.png"
  },
  badges: ["Demo", "HLS"],
  score: 8.0,
  releaseYear: 2008,
  liveNow: false,
  carry: {}
};

function ok(payload, paging) {
  return {
    ok: true,
    payload: payload,
    paging: paging || null,
    fault: null
  };
}

function fetchFeed(page, token) {
  return ok({
    spotlight: [demoEntry],
    shortcuts: [],
    shelves: [
      {
        shelfId: "demo-movies",
        heading: "Demo Movies",
        cardStyle: "wide",
        expandable: false,
        carry: {},
        entries: [demoEntry]
      }
    ]
  }, { current: 1, hasMore: false, token: null, totalItems: 1 });
}

function queryCatalog(request) {
  const keyword = String((request && request.keyword) || "").toLowerCase();
  const entries = !keyword || demoEntry.name.toLowerCase().indexOf(keyword) >= 0
    ? [demoEntry]
    : [];
  return ok({ entries: entries, facets: [] }, {
    current: 1,
    hasMore: false,
    token: null,
    totalItems: entries.length
  });
}

function fetchProfile(uid, carry, net) {
  if (uid !== demoEntry.uid) {
    return {
      ok: false,
      payload: null,
      paging: null,
      fault: {
        reason: "NOTHING_RESOLVABLE",
        summary: "Demo item not found",
        recovery: "notify_only"
      }
    };
  }
  return ok({
    uid: demoEntry.uid,
    name: demoEntry.name,
    aliases: [],
    kind: "movie",
    availability: "stream_only",
    visuals: demoEntry.visuals,
    tagline: "A public playback test",
    synopsis: "Big Buck Bunny is an open movie used here to verify that a Tobi connector can browse and play an HLS stream.",
    facts: {
      releaseYear: 2008,
      runtimeMin: 10,
      score: 8.0,
      languages: ["English"]
    },
    genres: ["Animation", "Comedy"],
    badges: ["Open Movie"],
    credits: [],
    directTarget: {
      targetId: "bbb-hls",
      carry: {},
      net: null
    },
    groups: [],
    relations: [],
    similar: []
  });
}

function fetchUnits(groupId, page, token, carry) {
  return ok({ groupId: groupId, units: [] }, {
    current: 1,
    hasMore: false,
    token: null,
    totalItems: 0
  });
}

function resolveMedia(targetId, carry, net) {
  if (targetId !== "bbb-hls") {
    return {
      ok: false,
      payload: null,
      paging: null,
      fault: {
        reason: "NOTHING_RESOLVABLE",
        summary: "Demo stream not found",
        recovery: "notify_only"
      }
    };
  }
  return ok({
    sources: [
      {
        sourceId: "bbb-hls-main",
        hostName: "Mux Test Streams",
        displayLabel: "Auto HLS",
        resolutionState: "ready",
        uri: "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
        container: "hls",
        qualityTag: "auto",
        priority: 100,
        canDirectSave: false,
        sidecarAudio: [],
        captions: [],
        decoderHint: "auto",
        resolverCarry: {}
      }
    ],
    acquisitions: [],
    sharedCaptions: [],
    timeline: null,
    playbackHints: {
      isLive: false,
      resumeOffsetMs: 0
    }
  });
}

function resolveDeferredSource(sourceId, resolverCarry) {
  return {
    ok: false,
    payload: null,
    paging: null,
    fault: {
      reason: "NOTHING_RESOLVABLE",
      summary: "This demo does not use deferred sources",
      recovery: "notify_only"
    }
  };
}
