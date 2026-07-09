package com.netflexity.anypoint.mcp.model;

import lombok.Builder;
import lombok.Data;

/**
 * Outcome of a redrive (replay) operation from a dead-letter queue back to a
 * target destination. Redrive is client-side consume-then-republish, so it is
 * at-least-once and republished messages receive NEW messageIds.
 */
@Data
@Builder
public class RedriveResult {
    private boolean dryRun;
    private String dlqQueue;
    private String targetDestination;
    private int attempted;
    private int redriven;
    private int failed;
    private int notAcked;   // published but the DLQ ACK failed -> may duplicate on a later run
    private int skipped;    // messages missing messageId/lockId, left in place
    private String note;
}
