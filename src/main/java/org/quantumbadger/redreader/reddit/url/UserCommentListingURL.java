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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.quantumbadger.redreader.R;
import org.quantumbadger.redreader.common.Constants;
import org.quantumbadger.redreader.common.General;
import org.quantumbadger.redreader.common.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class UserCommentListingURL extends CommentListingURL {

	public final String user;
	public final Sort order;
	public final Integer limit;
	public final String after;

	UserCommentListingURL(
			final String user,
			final Sort order,
			final Integer limit,
			final String after) {
		this.user = user;
		this.order = order;
		this.limit = limit;
		this.after = after;
	}

	@Override
	public UserCommentListingURL after(final String newAfter) {
		return new UserCommentListingURL(user, order, limit, newAfter);
	}

	@Override
	public UserCommentListingURL limit(final Integer newLimit) {
		return new UserCommentListingURL(user, order, newLimit, after);
	}

	public UserCommentListingURL order(final Sort newOrder) {
		return new UserCommentListingURL(user, newOrder, limit, after);
	}

	public static UserCommentListingURL parse(final Uri uri) {

		final String[] pathSegments;
		{
			final List<String> pathSegmentsList = uri.getPathSegments();

			final ArrayList<String> pathSegmentsFiltered = new ArrayList<>(
					pathSegmentsList.size());
			for(String segment : pathSegmentsList) {

				while(StringUtils.asciiLowercase(segment).endsWith(".json")
						|| StringUtils.asciiLowercase(segment).endsWith(".xml")) {
					segment = segment.substring(0, segment.lastIndexOf('.'));
				}

				if(!segment.isEmpty()) {
					pathSegmentsFiltered.add(segment);
				}
			}

			pathSegments
					= pathSegmentsFiltered.toArray(new String[0]);
		}

		final Sort order;
		if(pathSegments.length > 0) {
			order = Sort.parse(uri.getQueryParameter("sort"), uri.getQueryParameter("t"));
		} else {
			order = null;
		}

		if(pathSegments.length < 3) {
			return null;
		}

		if(!pathSegments[0].equalsIgnoreCase("user") && !pathSegments[0].equalsIgnoreCase(
				"u")) {
			return null;
		}

		// TODO validate username with regex
		final String username = pathSegments[1];
		final String typeName = pathSegments[2];

		if(!typeName.equalsIgnoreCase("comments")) {
			return null;
		}

		Integer limit = null;
		String after = null;

		for(final String parameterKey : General.getUriQueryParameterNames(uri)) {

			if(parameterKey.equalsIgnoreCase("after")) {
				after = uri.getQueryParameter(parameterKey);

			} else if(parameterKey.equalsIgnoreCase("limit")) {
				try {
					limit = Integer.parseInt(uri.getQueryParameter(parameterKey));
				} catch(final Throwable ignored) {
				}
			}
		}

		return new UserCommentListingURL(username, order, limit, after);
	}

	@Override
	public Uri generateJsonUri() {

		final Uri.Builder builder = new Uri.Builder();
		builder.scheme(Constants.Reddit.getScheme())
				.authority(Constants.Reddit.getDomain());

		builder.appendEncodedPath("user");
		builder.appendPath(user);
		builder.appendEncodedPath("comments");

		if(order != null) {
			order.addToUserCommentListingUri(builder);
		}

		if(after != null) {
			builder.appendQueryParameter("after", after);
		}

		if(limit != null) {
			builder.appendQueryParameter("limit", String.valueOf(limit));
		}

		builder.appendEncodedPath(".json");

		return builder.build();
	}

	@Override
	public @RedditURLParser.PathType
	int pathType() {
		return RedditURLParser.USER_COMMENT_LISTING_URL;
	}

	@Override
	public String humanReadableName(final Context context, final boolean shorter) {

		final String name = context.getString(R.string.user_comments);

		if(shorter) {
			return name;
		} else {
			return String.format("%s (%s)", name, user);
		}
	}

	public enum Sort {
		NEW,
		HOT,
		CONTROVERSIAL_HOUR,
		CONTROVERSIAL_DAY,
		CONTROVERSIAL_WEEK,
		CONTROVERSIAL_MONTH,
		CONTROVERSIAL_YEAR,
		CONTROVERSIAL_ALL,
		TOP_HOUR,
		TOP_DAY,
		TOP_WEEK,
		TOP_MONTH,
		TOP_YEAR,
		TOP_ALL;

		@Nullable
		public static Sort parse(@Nullable String sort, @Nullable String t) {

			if(sort == null) {
				return null;
			}

			sort = StringUtils.asciiLowercase(sort);
			t = t != null ? StringUtils.asciiLowercase(t) : null;

			if(sort.equals("hot")) {
				return HOT;

			} else if(sort.equals("new")) {
				return NEW;

			} else if(sort.equals("controversial")) {
				if(t == null) {
					return CONTROVERSIAL_ALL;
				} else if(t.equals("all")) {
					return CONTROVERSIAL_ALL;
				} else if(t.equals("hour")) {
					return CONTROVERSIAL_HOUR;
				} else if(t.equals("day")) {
					return CONTROVERSIAL_DAY;
				} else if(t.equals("week")) {
					return CONTROVERSIAL_WEEK;
				} else if(t.equals("month")) {
					return CONTROVERSIAL_MONTH;
				} else if(t.equals("year")) {
					return CONTROVERSIAL_YEAR;
				} else {
					return CONTROVERSIAL_ALL;
				}

			} else if(sort.equals("top")) {

				if(t == null) {
					return TOP_ALL;
				} else if(t.equals("all")) {
					return TOP_ALL;
				} else if(t.equals("hour")) {
					return TOP_HOUR;
				} else if(t.equals("day")) {
					return TOP_DAY;
				} else if(t.equals("week")) {
					return TOP_WEEK;
				} else if(t.equals("month")) {
					return TOP_MONTH;
				} else if(t.equals("year")) {
					return TOP_YEAR;
				} else {
					return TOP_ALL;
				}

			} else {
				return null;
			}
		}

		public void addToUserCommentListingUri(@NonNull final Uri.Builder builder) {

			switch(this) {
				case HOT:
				case NEW:
					builder.appendQueryParameter("sort", StringUtils.asciiLowercase(name()));
					break;

				case CONTROVERSIAL_HOUR:
				case CONTROVERSIAL_DAY:
				case CONTROVERSIAL_WEEK:
				case CONTROVERSIAL_MONTH:
				case CONTROVERSIAL_YEAR:
				case CONTROVERSIAL_ALL:
				case TOP_HOUR:
				case TOP_DAY:
				case TOP_WEEK:
				case TOP_MONTH:
				case TOP_YEAR:
				case TOP_ALL:
					final String[] parts = name().split("_");
					builder.appendQueryParameter(
							"sort",
							StringUtils.asciiLowercase(parts[0]));
					builder.appendQueryParameter("t", StringUtils.asciiLowercase(parts[1]));
					break;
			}
		}
	}
}
