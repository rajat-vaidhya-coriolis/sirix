package org.sirix.xquery.function.sdb.io;

import java.time.Instant;
import java.util.ArrayList;
import org.brackit.xquery.QueryContext;
import org.brackit.xquery.QueryException;
import org.brackit.xquery.atomic.DateTime;
import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.atomic.Str;
import org.brackit.xquery.function.AbstractFunction;
import org.brackit.xquery.module.StaticContext;
import org.brackit.xquery.sequence.ItemSequence;
import org.brackit.xquery.xdm.Sequence;
import org.brackit.xquery.xdm.Signature;
import org.sirix.xquery.function.sdb.SDBFun;
import org.sirix.xquery.node.DBCollection;
import org.sirix.xquery.node.DBNode;

public final class OpenRevisions extends AbstractFunction {

  /** Doc function name. */
  public final static QNm OPEN_REVISIONS = new QNm(SDBFun.SDB_NSURI, SDBFun.SDB_PREFIX, "open-revisions");

  /**
   * Constructor.
   *
   * @param name the name of the function
   * @param signature the signature of the function
   */
  public OpenRevisions(final QNm name, final Signature signature) {
    super(name, signature, true);
  }

  @Override
  public Sequence execute(final StaticContext sctx, final QueryContext ctx, final Sequence[] args) {
    if (args.length != 4) {
      throw new QueryException(new QNm("No valid arguments specified!"));
    }

    final var col = (DBCollection) ctx.getStore().lookup(((Str) args[0]).stringValue());

    if (col == null) {
      throw new QueryException(new QNm("No valid arguments specified!"));
    }

    final var expResName = ((Str) args[1]).stringValue();
    final var startDateTime = ((DateTime) args[2]).stringValue();
    final var startPointInTime = Instant.parse(startDateTime);
    final var endDateTime = ((DateTime) args[3]).stringValue();
    final var endPointInTime = Instant.parse(endDateTime);

    if (!startPointInTime.isBefore(endPointInTime))
      throw new QueryException(new QNm("No valid arguments specified!"));

    final var startDocNode = col.getDocument(startPointInTime, expResName);
    final var endDocNode = col.getDocument(endPointInTime, expResName);

    var startRevision = startDocNode.getTrx().getRevisionNumber();
    final int endRevision = endDocNode.getTrx().getRevisionNumber();

    final var documentNodes = new ArrayList<DBNode>();
    documentNodes.add(startDocNode);

    while (++startRevision < endRevision) {
      documentNodes.add(col.getDocument(startRevision, expResName));
    }

    documentNodes.add(endDocNode);

    return new ItemSequence(documentNodes.toArray(new DBNode[documentNodes.size()]));
  }
}
