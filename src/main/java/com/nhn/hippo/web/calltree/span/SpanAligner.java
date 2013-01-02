package com.nhn.hippo.web.calltree.span;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.profiler.common.bo.SpanBo;

/**
 *
 */
public class SpanAligner {

    public static final Long SPAN_ROOT = -1L;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private List<SpanBo> spans;

    //    private Map<Long, Span> spanIdMap;
    private Map<Long, List<SpanBo>> parentSpanIdMap;

    private int depth = 0;

    private static final Comparator<SpanBo> timeComparator = new Comparator<SpanBo>() {
        @Override
        public int compare(SpanBo o1, SpanBo o2) {
            long o1Timestamp = o1.getStartTime();
            long o2Timestamp = o2.getStartTime();
            if (o1Timestamp > o2Timestamp) {
                return 1;
            }
            if (o1Timestamp == o2Timestamp) {
                return 0;
            }
            return -1;
        }
    };

    public SpanAligner(List<SpanBo> spans) {
        this.spans = spans;
    }

    public List<SpanAlign> sort() {
        buildIndex();

        List<SpanAlign> result = new ArrayList<SpanAlign>(spans.size());

        SpanBo root = findRoot();
        logger.debug("find root {}", root);
        result.add(new SpanAlign(0, root));

        List<SpanBo> next = nextSpan(root);
        doNext(next, result);
        return result;
    }

    public void buildIndex() {
        SpanIdChecker spanIdCheck = new SpanIdChecker(spans);
        Map<Long, List<SpanBo>> parentSpanIdMap = new HashMap<Long, List<SpanBo>>();

        for (SpanBo span : spans) {
            spanIdCheck.check(span);

            long parentSpanId = span.getParentSpanId();
            List<SpanBo> spanList = parentSpanIdMap.get(parentSpanId);
            if (spanList != null) {
                spanList.add(span);
            } else {
                LinkedList<SpanBo> newSpanList = new LinkedList<SpanBo>();
                newSpanList.add(span);
                parentSpanIdMap.put(parentSpanId, newSpanList);
            }
        }
        this.depth = 0;
//        this.spanIdMap = spanIdMap;
        this.parentSpanIdMap = parentSpanIdMap;
    }

    private void doNext(List<SpanBo> spans, List<SpanAlign> result) {
        if (spans == null) {
            return;
        }
        depth++;
        try {
            for (SpanBo next : spans) {
                if (logger.isDebugEnabled()) {
                    logger.debug("{} {} next {}", new Object[]{getSpace(), depth, next});
                }

                result.add(new SpanAlign(depth, next));

                List<SpanBo> nextSpan = nextSpan(next);
                doNext(nextSpan, result);
            }
        } finally {
            depth--;
        }
    }


    private String getSpace() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            sb.append("  ");
        }
        return sb.toString();
    }


    private List<SpanBo> nextSpan(SpanBo parent) {
        List<SpanBo> child = this.parentSpanIdMap.get(parent.getSpanId());
        if (child == null) {
            return null;
        }
        this.parentSpanIdMap.remove(parent.getSpanId());
        // 동일 레벨은 시간순으로 정렬.
        Collections.sort(child, timeComparator);
        return child;
    }


    private SpanBo findRoot() {
        List<SpanBo> root = this.parentSpanIdMap.get(SPAN_ROOT);
        if (root == null) {
            logger.warn("root span not found. {}", spans);
            throw new IllegalStateException("root span not found");
        }
        if (root.size() == -1) {
            logger.info("invalid root span. duplicated root span {}", root);
            throw new IllegalStateException("duplicated root span");
        }
        return root.get(0);
    }

    public static class SpanIdChecker {
        private Map<Long, SpanBo> spanCheck = new HashMap<Long, SpanBo>();
        private List<SpanBo> spans;

        public SpanIdChecker(List<SpanBo> spans) {
            this.spans = spans;
        }

        public void check(SpanBo span) {
            SpanBo before = spanCheck.put(span.getSpanId(), span);
            if (before != null) {
                // span id 중복체크
                deplicatedSpanIdDump(span);
                throw new IllegalStateException("duplicated spanId. id:" + span.getSpanId());
            }
        }

        private void deplicatedSpanIdDump(SpanBo span) {
            // 중복 span dump
            Logger internalLog = LoggerFactory.getLogger(this.getClass());
            internalLog.info("duplicated spanId {}, list:{}", span, spans);
        }
    }
}
