/*******************************************************************************
 * This file is part of RedReader.
 *
 * RedReader is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * RedReader is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with RedReader.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/

package org.quantumbadger.redreader.reddit.url;

import android.content.Context;
import android.net.Uri;
import org.quantumbadger.redreader.R;
import org.quantumbadger.redreader.common.Constants;
import org.quantumbadger.redreader.common.General;
import org.quantumbadger.redreader.common.StringUtils;
import org.quantumbadger.redreader.reddit.PostSort;

import java.util.ArrayList;
import java.util.List;

public class SearchPostListURL extends PostListingURL {

	public final String subreddit;
	public final String query;

	public final PostSort order;
	public final Integer limit;
	public final String before;
	public final String after;

	SearchPostListURL(
			final String subreddit,
			final String query,
			final PostSort order,
			final Integer limit,
			final String before,
			final String after) {
		this.subreddit = subreddit;
		this.query = query;
		this.order = order;
		this.limit = limit;
		this.before = before;
		this.after = after;
	}

	SearchPostListURL(
			final String subreddit,
			final String query,
			final Integer limit,
			final String before,
			final String after) {
		this(subreddit, query, PostSort.RELEVANCE_ALL, limit, before, after);
	}

	public static SearchPostListURL build(String subreddit, final String query) {
		if(subreddit != null) {
			while(subreddit.startsWith("/")) {
				subreddit = subreddit.substring(1);
			}
			while(subreddit.startsWith("r/")) {
				subreddit = subreddit.substring(2);
			}
		}
		return new SearchPostListURL(subreddit, query, null, null, null);
	}

	@Override
	public PostListingURL after(final String after) {
		return new SearchPostListURL(subreddit, query, order, limit, before, after);
	}

	@Override
	public PostListingURL limit(final Integer limit) {
		return new SearchPostListURL(subreddit, query, order, limit, before, after);
	}

	public SearchPostListURL sort(final PostSort newOrder) {
		return new SearchPostListURL(subreddit, query, newOrder, limit, before, after);
	}

	@Override
	public Uri generateJsonUri() {

		final Uri.Builder builder = new Uri.Builder();
		builder.scheme(Constants.Reddit.getScheme())
				.authority(Constants.Reddit.getDomain());

		if(subreddit != null) {
			builder.encodedPath("/r/");
			builder.appendPath(subreddit);
			builder.appendQueryParameter("restrict_sr", "on");
		} else {
			builder.encodedPath("/");
		}

		builder.appendEncodedPath("search");

		if(query != null) {
			builder.appendQueryParameter("q", query);
		}

		if(order != null) {
			switch(order) {
				case RELEVANCE_HOUR:
				case RELEVANCE_DAY:
				case RELEVANCE_WEEK:
				case RELEVANCE_MONTH:
				case RELEVANCE_YEAR:
				case RELEVANCE_ALL:
				case NEW_HOUR:
				case NEW_DAY:
				case NEW_WEEK:
				case NEW_MONTH:
				case NEW_YEAR:
				case NEW_ALL:
				case HOT_HOUR:
				case HOT_DAY:
				case HOT_WEEK:
				case HOT_MONTH:
				case HOT_YEAR:
				case HOT_ALL:
				case TOP_HOUR:
				case TOP_DAY:
				case TOP_WEEK:
				case TOP_MONTH:
				case TOP_YEAR:
				case TOP_ALL:
				case COMMENTS_HOUR:
				case COMMENTS_DAY:
				case COMMENTS_WEEK:
				case COMMENTS_MONTH:
				case COMMENTS_YEAR:
				case COMMENTS_ALL:
					final String[] parts = order.name().split("_");
					builder.appendQueryParameter("sort", StringUtils.asciiLowercase(parts[0]));
					builder.appendQueryParameter("t", StringUtils.asciiLowercase(parts[1]));
					break;
			}
		}

		if(before != null) {
			builder.appendQueryParameter("before", before);
		}

		if(after != null) {
			builder.appendQueryParameter("after", after);
		}

		if(limit != null) {
			builder.appendQueryParameter("limit", String.valueOf(limit));
		}

		builder.appendEncodedPath(".json");

		// if the user doesn't have NSFW content disabled, it won't show up anyway
		// leaving this on by default doesn't hurt
		builder.appendQueryParameter("include_over_18", "on");

		return builder.build();
	}

	@Override
	public @RedditURLParser.PathType
	int pathType() {
		return RedditURLParser.SEARCH_POST_LISTING_URL;
	}

	public static SearchPostListURL parse(final Uri uri) {

		boolean restrictSubreddit = false;
		String query = "";
		final PostSort order;
		Integer limit = null;
		String before = null;
		String after = null;

		String sortParam = null;
		String timeParam = null;

		for(final String parameterKey : General.getUriQueryParameterNames(uri)) {

			if(parameterKey.equalsIgnoreCase("after")) {
				after = uri.getQueryParameter(parameterKey);

			} else if(parameterKey.equalsIgnoreCase("before")) {
				before = uri.getQueryParameter(parameterKey);

			} else if(parameterKey.equalsIgnoreCase("limit")) {
				try {
					limit = Integer.parseInt(uri.getQueryParameter(parameterKey));
				} catch(final Throwable ignored) {
				}

			} else if(parameterKey.equalsIgnoreCase("sort")) {
				sortParam = uri.getQueryParameter(parameterKey);

			} else if(parameterKey.equalsIgnoreCase("t")) {
				timeParam = uri.getQueryParameter(parameterKey);

			} else if(parameterKey.equalsIgnoreCase("q")) {
				query = uri.getQueryParameter(parameterKey);

			} else if(parameterKey.equalsIgnoreCase("restrict_sr")) {
				restrictSubreddit = "on".equalsIgnoreCase(uri.getQueryParameter(parameterKey));
			}
		}

		order = PostSort.parseSearch(sortParam, timeParam);

		final String[] pathSegments;
		{
			final List<String> pathSegmentsList = uri.getPathSegments();

			final ArrayList<String> pathSegmentsFiltered =
					new ArrayList<>(pathSegmentsList.size());
			for(String segment : pathSegmentsList) {

				while(StringUtils.asciiLowercase(segment).endsWith(".json")
						|| StringUtils.asciiLowercase(segment).endsWith(".xml")) {
					segment = segment.substring(0, segment.lastIndexOf('.'));
				}

				if(!segment.isEmpty()) {
					pathSegmentsFiltered.add(segment);
				}
			}

			pathSegments =
					pathSegmentsFiltered.toArray(new String[0]);
		}

		if(pathSegments.length != 1 && pathSegments.length != 3) {
			return null;
		}
		if(!pathSegments[pathSegments.length - 1].equalsIgnoreCase("search")) {
			return null;
		}

		switch(pathSegments.length) {

			case 1: {
				return new SearchPostListURL(null, query, order, limit, before, after);
			}

			case 3: {

				if(!pathSegments[0].equals("r")) {
					return null;
				}

				final String subreddit = pathSegments[1];
				return new SearchPostListURL(
						restrictSubreddit ? subreddit : null,
						query,
						order,
						limit,
						before,
						after);
			}

			default:
				return null;
		}
	}

	@Override
	public String humanReadableName(final Context context, final boolean shorter) {

		if(shorter) {
			return context.getString(R.string.search_results_short);
		}

		if(query != null && subreddit != null) {
			return String.format(
					context.getString(R.string.search_results_all),
					query,
					subreddit);
		} else if(query != null) {
			return String.format(
					context.getString(R.string.search_results_query_only),
					query);
		} else if(subreddit != null) {
			return String.format(
					context.getString(R.string.search_results_subreddit_only),
					subreddit);
		}

		return context.getString(R.string.action_search);
	}

	@Override
	public String humanReadablePath() {
		final StringBuilder builder = new StringBuilder(super.humanReadablePath());

		if(query != null) {
			builder.append("?q=").append(query);
		}

		return builder.toString();
	}
}
