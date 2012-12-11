/**
 * Copyright (c) 2011, University of Konstanz, Distributed Systems Group
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 * * Neither the name of the University of Konstanz nor the
 * names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.sirix.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;

import org.sirix.api.PageReadTrx;
import org.sirix.cache.RecordPageContainer;
import org.sirix.node.interfaces.Record;
import org.sirix.page.interfaces.KeyValuePage;

/**
 * Enum for providing different revision algorithms. Each kind must implement
 * one method to reconstruct key/value pages for modification and for reading.
 * 
 * @author Sebastian Graf, University of Konstanz
 * @author Johannes Lichtenberger, University of Konstanz
 * 
 */
public enum Revisioning {

	/**
	 * FullDump, just dumping the complete older revision.
	 */
	FULL {
		@Override
		public <K extends Comparable<? super K>, V extends Record, T extends KeyValuePage<K, V>> T combineRecordPages(
				final @Nonnull List<T> pages, final @Nonnegative int revToRestore,
				final @Nonnull PageReadTrx pageReadTrx) {
			assert pages.size() == 1 : "Only one version of the page!";
			return pages.get(0);
		}

		@Override
		public <K extends Comparable<? super K>, V extends Record, T extends KeyValuePage<K, V>> RecordPageContainer<T> combineRecordPagesForModification(
				final @Nonnull List<T> pages, final @Nonnegative int mileStoneRevision,
				final @Nonnull PageReadTrx pageReadTrx) {
			assert pages.size() == 1;
			final T firstPage = pages.get(0);
			final long recordPageKey = firstPage.getPageKey();
			final List<T> returnVal = new ArrayList<>(2);
			returnVal.add(firstPage.<T> newInstance(recordPageKey,
					firstPage.getRevision() + 1, pageReadTrx));
			returnVal.add(firstPage.<T> newInstance(recordPageKey,
					firstPage.getRevision() + 1, pageReadTrx));

			for (final Map.Entry<K, V> entry : pages.get(0).entrySet()) {
				returnVal.get(0).setEntry(entry.getKey(), entry.getValue());
				returnVal.get(1).setEntry(entry.getKey(), entry.getValue());
			}

			final RecordPageContainer<T> cont = new RecordPageContainer<>(
					returnVal.get(0), returnVal.get(1));
			return cont;
		}
	},

	/**
	 * Differential. Only the diffs are stored related to the last milestone
	 * revision.
	 */
	DIFFERENTIAL {
		@Override
		public <K extends Comparable<? super K>, V extends Record, T extends KeyValuePage<K, V>> T combineRecordPages(
				final @Nonnull List<T> pages, final @Nonnegative int revToRestore,
				final @Nonnull PageReadTrx pageReadTrx) {
			assert pages.size() <= 2;
			final T firstPage = pages.get(0);
			final long recordPageKey = firstPage.getPageKey();
			final T returnVal = firstPage.newInstance(recordPageKey,
					firstPage.getRevision(), pageReadTrx);
			if (pages.size() == 2) {
				returnVal.setDirty(true);
			}
			final T latest = pages.get(0);
			T fullDump = pages.size() == 1 ? pages.get(0) : pages.get(1);

			assert latest.getPageKey() == recordPageKey;
			assert fullDump.getPageKey() == recordPageKey;

			for (final Map.Entry<K, V> entry : latest.entrySet()) {
				returnVal.setEntry(entry.getKey(), entry.getValue());
			}

			// Skip full dump if not needed (fulldump equals latest page).
			if (pages.size() == 2) {
				for (final Entry<K, V> entry : fullDump.entrySet()) {
					if (returnVal.getValue(entry.getKey()) == null) {
						returnVal.setEntry(entry.getKey(), entry.getValue());
					}
				}
			}
			return returnVal;
		}

		@Override
		public <K extends Comparable<? super K>, V extends Record, T extends KeyValuePage<K, V>> RecordPageContainer<T> combineRecordPagesForModification(
				final @Nonnull List<T> pages, final @Nonnegative int revToRestore,
				final @Nonnull PageReadTrx pageReadTrx) {
			assert pages.size() <= 2;
			final T firstPage = pages.get(0);
			final long recordPageKey = firstPage.getPageKey();
			final List<T> returnVal = new ArrayList<>(2);
			returnVal.add(firstPage.<T> newInstance(recordPageKey,
					firstPage.getRevision() + 1, pageReadTrx));
			returnVal.add(firstPage.<T> newInstance(recordPageKey,
					firstPage.getRevision() + 1, pageReadTrx));

			final T latest = firstPage;
			T fullDump = pages.size() == 1 ? firstPage : pages.get(1);

			for (final Map.Entry<K, V> entry : fullDump.entrySet()) {
				returnVal.get(0).setEntry(entry.getKey(), entry.getValue());

				if ((latest.getRevision() + 1) % revToRestore == 0) {
					// Fulldump.
					returnVal.get(1).setEntry(entry.getKey(), entry.getValue());
				}
			}

			// iterate through all nodes
			for (final Map.Entry<K, V> entry : latest.entrySet()) {
				returnVal.get(0).setEntry(entry.getKey(), entry.getValue());
				returnVal.get(1).setEntry(entry.getKey(), entry.getValue());
			}

			final RecordPageContainer<T> cont = new RecordPageContainer<>(
					returnVal.get(0), returnVal.get(1));
			return cont;
		}
	},

	/**
	 * Incremental revisioning. Each revision can be reconstructed with the help
	 * of the last full-dump plus the incremental steps between.
	 */
	INCREMENTAL {
		@Override
		public <K extends Comparable<? super K>, V extends Record, T extends KeyValuePage<K, V>> T combineRecordPages(
				final @Nonnull List<T> pages, final @Nonnegative int revToRestore,
				final @Nonnull PageReadTrx pageReadTrx) {
			assert pages.size() <= revToRestore;
			final T firstPage = pages.get(0);
			final long recordPageKey = firstPage.getPageKey();
			final T returnVal = firstPage.newInstance(firstPage.getPageKey(),
					firstPage.getRevision(), firstPage.getPageReadTrx());
			if (pages.size() > 1) {
				returnVal.setDirty(true);
			}

			for (final KeyValuePage<K, V> page : pages) {
				assert page.getPageKey() == recordPageKey;
				for (final Entry<K, V> entry : page.entrySet()) {
					final K nodeKey = entry.getKey();
					if (returnVal.getValue(nodeKey) == null) {
						returnVal.setEntry(nodeKey, entry.getValue());
					}
				}
			}

			return returnVal;
		}

		@Override
		public <K extends Comparable<? super K>, V extends Record, T extends KeyValuePage<K, V>> RecordPageContainer<T> combineRecordPagesForModification(
				final @Nonnull List<T> pages, final int revToRestore,
				final @Nonnull PageReadTrx pageReadTrx) {
			final T firstPage = pages.get(0);
			final long recordPageKey = firstPage.getPageKey();
			final List<T> returnVal = new ArrayList<>(2);
			returnVal.add(firstPage.<T> newInstance(recordPageKey,
					firstPage.getRevision() + 1, pageReadTrx));
			returnVal.add(firstPage.<T> newInstance(recordPageKey,
					firstPage.getRevision() + 1, pageReadTrx));

			for (final T page : pages) {
				assert page.getPageKey() == recordPageKey;

				for (final Entry<K, V> entry : page.entrySet()) {
					// Caching the complete page.
					final K key = entry.getKey();
					assert key != null;
					if (entry != null && returnVal.get(0).getValue(key) == null) {
						returnVal.get(0).setEntry(key, entry.getValue());

						if (returnVal.get(1).getValue(entry.getKey()) == null
								&& returnVal.get(0).getRevision() % revToRestore == 0) {
							returnVal.get(1).setEntry(key, entry.getValue());
						}
					}
				}
			}

			final RecordPageContainer<T> cont = new RecordPageContainer<>(
					returnVal.get(0), returnVal.get(1));
			return cont;
		}
	};

	/**
	 * Method to reconstruct a complete {@link KeyValuePage} with the help of
	 * partly filled pages plus a revision-delta which determines the necessary
	 * steps back.
	 * 
	 * @param pages
	 *          the base of the complete {@link KeyValuePage}
	 * @param revToRestore
	 *          the revision needed to build up the complete milestone
	 * @return the complete {@link KeyValuePage}
	 */
	public abstract <K extends Comparable<? super K>, V extends Record, T extends KeyValuePage<K, V>> T combineRecordPages(
			final @Nonnull List<T> pages, final @Nonnegative int revToRestore,
			final @Nonnull PageReadTrx pageReadTrx);

	/**
	 * Method to reconstruct a complete {@link KeyValuePage} for reading as well
	 * as a {@link KeyValuePage} for serializing with the nodes to write.
	 * 
	 * @param pages
	 *          the base of the complete {@link KeyValuePage}
	 * @param mileStoneRevision
	 *          the revision needed to build up the complete milestone
	 * @return a {@link RecordPageContainer} holding a complete
	 *         {@link KeyValuePage} for reading and one for writing
	 */
	public abstract <K extends Comparable<? super K>, V extends Record, T extends KeyValuePage<K, V>> RecordPageContainer<T> combineRecordPagesForModification(
			final @Nonnull List<T> pages, final @Nonnegative int mileStoneRevision,
			final @Nonnull PageReadTrx pageReadTrx);
}
