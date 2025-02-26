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

package org.quantumbadger.redreader.fragments;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import org.quantumbadger.redreader.R;
import org.quantumbadger.redreader.activities.BaseActivity;
import org.quantumbadger.redreader.cache.CacheManager;
import org.quantumbadger.redreader.common.AndroidCommon;
import org.quantumbadger.redreader.common.General;
import org.quantumbadger.redreader.common.LinkHandler;
import org.quantumbadger.redreader.common.PrefsUtility;
import org.quantumbadger.redreader.reddit.prepared.RedditParsedPost;
import org.quantumbadger.redreader.reddit.prepared.RedditPreparedPost;
import org.quantumbadger.redreader.reddit.things.RedditPost;
import org.quantumbadger.redreader.reddit.url.RedditURLParser;
import org.quantumbadger.redreader.views.RedditPostView;
import org.quantumbadger.redreader.views.bezelmenu.BezelSwipeOverlay;
import org.quantumbadger.redreader.views.bezelmenu.SideToolbarOverlay;
import org.quantumbadger.redreader.views.webview.VideoEnabledWebChromeClient;
import org.quantumbadger.redreader.views.webview.WebViewFixed;

import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class WebViewFragment extends Fragment
		implements RedditPostView.PostSelectionListener {

	private BaseActivity mActivity;

	private String mUrl;
	private String html;
	private volatile String currentUrl;
	private volatile boolean goingBack;
	private volatile int lastBackDepthAttempt;

	private WebViewFixed webView;
	private ProgressBar progressView;
	private FrameLayout outer;

	public static WebViewFragment newInstance(final String url, final RedditPost post) {

		final WebViewFragment f = new WebViewFragment();

		final Bundle bundle = new Bundle(1);
		bundle.putString("url", url);
		if(post != null) {
			bundle.putParcelable("post", post);
		}
		f.setArguments(bundle);

		return f;
	}

	public static WebViewFragment newInstanceHtml(final String html) {

		final WebViewFragment f = new WebViewFragment();

		final Bundle bundle = new Bundle(1);
		bundle.putString("html", html);
		f.setArguments(bundle);

		return f;
	}

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		// TODO load position/etc?
		super.onCreate(savedInstanceState);
		mUrl = getArguments().getString("url");
		html = getArguments().getString("html");
	}

	@SuppressLint("SetJavaScriptEnabled")
	@Override
	public View onCreateView(
			final LayoutInflater inflater,
			final ViewGroup container,
			final Bundle savedInstanceState) {

		mActivity = (BaseActivity)getActivity();

		CookieSyncManager.createInstance(mActivity);

		outer = (FrameLayout)inflater.inflate(R.layout.web_view_fragment, null);

		final RedditPost srcPost = getArguments().getParcelable("post");
		final RedditPreparedPost post;

		if(srcPost != null) {

			final RedditParsedPost parsedPost = new RedditParsedPost(
					mActivity,
					srcPost,
					false);

			post = new RedditPreparedPost(
					mActivity,
					CacheManager.getInstance(mActivity),
					0,
					parsedPost,
					-1,
					false,
					false,
					false,
					false);

		} else {
			post = null;
		}

		webView = outer.findViewById(R.id.web_view_fragment_webviewfixed);
		final FrameLayout loadingViewFrame
				= outer.findViewById(R.id.web_view_fragment_loadingview_frame);

		progressView = new ProgressBar(
				mActivity,
				null,
				android.R.attr.progressBarStyleHorizontal);
		loadingViewFrame.addView(progressView);
		loadingViewFrame.setPadding(
				General.dpToPixels(mActivity, 10),
				0,
				General.dpToPixels(mActivity, 10),
				0);
		final FrameLayout fullscreenViewFrame
				= outer.findViewById(R.id.web_view_fragment_fullscreen_frame);

		final VideoEnabledWebChromeClient chromeClient = new VideoEnabledWebChromeClient(
				loadingViewFrame,
				fullscreenViewFrame) {
			@Override
			public void onProgressChanged(final WebView view, final int newProgress) {

				super.onProgressChanged(view, newProgress);

				AndroidCommon.UI_THREAD_HANDLER.post(() -> {
					progressView.setProgress(newProgress);
					progressView.setVisibility(newProgress == 100
							? View.GONE
							: View.VISIBLE);
				});
			}
		};

		chromeClient.setOnToggledFullscreen(fullscreen -> {
			// Your code to handle the full-screen change, for example showing
			// and hiding the title bar. Example:
			if(fullscreen) {
				final WindowManager.LayoutParams attrs = mActivity.getWindow()
						.getAttributes();
				attrs.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
				attrs.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
				mActivity.getWindow().setAttributes(attrs);
				mActivity.getSupportActionBar().hide();
				if(Build.VERSION.SDK_INT >= 14) {
					//noinspection all
					mActivity.getWindow()
							.getDecorView()
							.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
				}
			} else {
				final WindowManager.LayoutParams attrs = mActivity.getWindow()
						.getAttributes();
				//only re-enable status bar if there is no contradicting preference set
				if(!PrefsUtility.pref_appearance_hide_android_status()) {
					attrs.flags &= ~WindowManager.LayoutParams.FLAG_FULLSCREEN;
				}
				attrs.flags &= ~WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
				mActivity.getWindow().setAttributes(attrs);
				mActivity.getSupportActionBar().show();
				if(Build.VERSION.SDK_INT >= 14) {
					//noinspection all
					mActivity.getWindow()
							.getDecorView()
							.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
				}
			}

		});

		/*handle download links show an alert box to load this outside the internal browser*/
		webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength)
				-> new AlertDialog.Builder(mActivity)
						.setTitle(R.string.download_link_title)
						.setMessage(R.string.download_link_message)
						.setPositiveButton(
								android.R.string.yes,
								(dialog, which) -> {
									final Intent i = new Intent(Intent.ACTION_VIEW);
									i.setData(Uri.parse(url));

									try {
										getContext().startActivity(i);
										mActivity.onBackPressed(); //get back from internal browser

									} catch(final ActivityNotFoundException e) {
										General.quickToast(
												getContext(),
												R.string.action_not_handled_by_installed_app_toast);
									}
								})
						.setNegativeButton(
								android.R.string.no,
								(dialog, which) -> {
									mActivity.onBackPressed(); //get back from internal browser
								})
						.setIcon(android.R.drawable.ic_dialog_alert)
						.show());
		/*handle download links end*/


		final WebSettings settings = webView.getSettings();

		settings.setBuiltInZoomControls(true);
		settings.setJavaScriptEnabled(true);
		settings.setJavaScriptCanOpenWindowsAutomatically(false);
		settings.setUseWideViewPort(true);
		settings.setLoadWithOverviewMode(true);
		settings.setDomStorageEnabled(true);
		settings.setDisplayZoomControls(false);

		// TODO handle long clicks

		webView.setWebChromeClient(chromeClient);


		if(mUrl != null) {
			webView.loadUrl(mUrl);
		} else {
			webView.loadHtmlUTF8WithBaseURL("https://reddit.com/", html);
		}

		webView.setWebViewClient(new WebViewClient() {
			@Override
			public boolean shouldOverrideUrlLoading(
					final WebView view,
					final String url) {

				if(url == null) {
					return false;
				}

				if(url.startsWith("data:")) {
					// Prevent imgur bug where we're directed to some random data URI
					return true;
				}

				// Go back if loading same page to prevent redirect loops.
				if(goingBack && currentUrl != null && url.equals(currentUrl)) {

					General.quickToast(mActivity,
							String.format(
									Locale.US,
									"Handling redirect loop (level %d)",
									-lastBackDepthAttempt), Toast.LENGTH_SHORT);

					lastBackDepthAttempt--;

					if(webView.canGoBackOrForward(lastBackDepthAttempt)) {
						webView.goBackOrForward(lastBackDepthAttempt);
					} else {
						mActivity.finish();
					}
				} else {

					if(RedditURLParser.parse(Uri.parse(url)) != null) {
						LinkHandler.onLinkClicked(mActivity, url, false);
					} else {
						if(!PrefsUtility.pref_behaviour_useinternalbrowser()) {
							LinkHandler.openWebBrowser(
									mActivity,
									Uri.parse(url),
									true);
						} else if(PrefsUtility.pref_behaviour_usecustomtabs()
								&& Build.VERSION.SDK_INT
										>= Build.VERSION_CODES.JELLY_BEAN_MR2) {
							LinkHandler.openCustomTab(
									mActivity,
									Uri.parse(url),
									null);
						} else {
							webView.loadUrl(url);
							currentUrl = url;
						}
					}
				}

				return true;
			}

			@Override
			public void onPageStarted(final WebView view, final String url, final Bitmap favicon) {
				super.onPageStarted(view, url, favicon);

				if(mUrl != null && url != null) {

					final AppCompatActivity activity = mActivity;

					if(activity != null) {
						activity.setTitle(url);
					}
				}
			}

			@Override
			public void onPageFinished(final WebView view, final String url) {
				super.onPageFinished(view, url);

				new Timer().schedule(new TimerTask() {
					@Override
					public void run() {

						AndroidCommon.UI_THREAD_HANDLER.post(() -> {

							if(currentUrl == null || url == null) {
								return;
							}

							if(!url.equals(view.getUrl())) {
								return;
							}

							if(goingBack && url.equals(currentUrl)) {

								General.quickToast(
										mActivity,
										String.format(
												Locale.US,
												"Handling redirect loop (level %d)",
												-lastBackDepthAttempt));

								lastBackDepthAttempt--;

								if(webView.canGoBackOrForward(lastBackDepthAttempt)) {
									webView.goBackOrForward(lastBackDepthAttempt);
								} else {
									mActivity.finish();
								}

							} else {
								goingBack = false;
							}
						});
					}
				}, 1000);
			}

			@Override
			public void doUpdateVisitedHistory(
					final WebView view,
					final String url,
					final boolean isReload) {
				super.doUpdateVisitedHistory(view, url, isReload);
			}
		});

		final FrameLayout outerFrame = new FrameLayout(mActivity);
		outerFrame.addView(outer);

		if(post != null) {

			final SideToolbarOverlay toolbarOverlay = new SideToolbarOverlay(mActivity);

			final BezelSwipeOverlay bezelOverlay = new BezelSwipeOverlay(
					mActivity,
					new BezelSwipeOverlay.BezelSwipeListener() {
						@Override
						public boolean onSwipe(@BezelSwipeOverlay.SwipeEdge final int edge) {

							toolbarOverlay.setContents(post.generateToolbar(
									mActivity,
									false,
									toolbarOverlay));
							toolbarOverlay.show(edge == BezelSwipeOverlay.LEFT
									?
									SideToolbarOverlay.SideToolbarPosition.LEFT
									: SideToolbarOverlay.SideToolbarPosition.RIGHT);
							return true;
						}

						@Override
						public boolean onTap() {

							if(toolbarOverlay.isShown()) {
								toolbarOverlay.hide();
								return true;
							}

							return false;
						}
					});

			outerFrame.addView(bezelOverlay);
			outerFrame.addView(toolbarOverlay);

			General.setLayoutMatchParent(bezelOverlay);
			General.setLayoutMatchParent(toolbarOverlay);
		}

		return outerFrame;
	}

	@Override
	public void onDestroyView() {

		webView.stopLoading();
		webView.loadData("<html></html>", "text/plain", "UTF-8");
		webView.reload();
		webView.loadUrl("about:blank");
		outer.removeAllViews();
		webView.destroy();

		final CookieManager cookieManager = CookieManager.getInstance();
		cookieManager.removeAllCookie();

		super.onDestroyView();
	}

	public boolean onBackButtonPressed() {

		if(webView.canGoBack()) {
			goingBack = true;
			lastBackDepthAttempt = -1;
			webView.goBack();
			return true;
		}

		return false;
	}

	@Override
	public void onPostSelected(final RedditPreparedPost post) {
		((RedditPostView.PostSelectionListener)mActivity).onPostSelected(post);
	}

	@Override
	public void onPostCommentsSelected(final RedditPreparedPost post) {
		((RedditPostView.PostSelectionListener)mActivity).onPostCommentsSelected(post);
	}

	public String getCurrentUrl() {
		return (currentUrl != null) ? currentUrl : mUrl;
	}

	@Override
	public void onPause() {
		super.onPause();
		webView.onPause();
		webView.pauseTimers();
	}

	@Override
	public void onResume() {
		super.onResume();
		webView.resumeTimers();
		webView.onResume();
	}

	public void clearCache() {
		webView.clearBrowser();
	}
}
