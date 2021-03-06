package org.wordpress.android.fluxc.network.xmlrpc;

import com.android.volley.Request;
import com.android.volley.RequestQueue;

import org.wordpress.android.fluxc.Dispatcher;
import org.wordpress.android.fluxc.generated.AuthenticationActionBuilder;
import org.wordpress.android.fluxc.model.SiteModel;
import org.wordpress.android.fluxc.network.BaseRequest;
import org.wordpress.android.fluxc.network.BaseRequest.OnAuthFailedListener;
import org.wordpress.android.fluxc.network.BaseRequest.OnParseErrorListener;
import org.wordpress.android.fluxc.network.HTTPAuthManager;
import org.wordpress.android.fluxc.network.UserAgent;
import org.wordpress.android.fluxc.network.discovery.DiscoveryRequest;
import org.wordpress.android.fluxc.network.discovery.DiscoveryXMLRPCRequest;
import org.wordpress.android.fluxc.network.rest.wpcom.auth.AccessToken;
import org.wordpress.android.fluxc.store.AccountStore.AuthenticateErrorPayload;
import org.wordpress.android.fluxc.utils.ErrorUtils.OnUnexpectedError;

public class BaseXMLRPCClient {
    private AccessToken mAccessToken;
    private SiteModel mSiteModel;
    private final RequestQueue mRequestQueue;
    protected final Dispatcher mDispatcher;
    protected UserAgent mUserAgent;
    protected HTTPAuthManager mHTTPAuthManager;

    protected OnAuthFailedListener mOnAuthFailedListener;
    protected OnParseErrorListener mOnParseErrorListener;

    public BaseXMLRPCClient(Dispatcher dispatcher, RequestQueue requestQueue, AccessToken accessToken,
                            UserAgent userAgent, HTTPAuthManager httpAuthManager) {
        mRequestQueue = requestQueue;
        mDispatcher = dispatcher;
        mAccessToken = accessToken;
        mUserAgent = userAgent;
        mHTTPAuthManager = httpAuthManager;
        mOnAuthFailedListener = new OnAuthFailedListener() {
            @Override
            public void onAuthFailed(AuthenticateErrorPayload authError) {
                mDispatcher.dispatch(AuthenticationActionBuilder.newAuthenticateErrorAction(authError));
            }
        };
        mOnParseErrorListener = new OnParseErrorListener() {
            @Override
            public void onParseError(OnUnexpectedError event) {
                mDispatcher.emitChange(event);
            }
        };
    }

    public Request add(XMLRPCRequest request) {
        return mRequestQueue.add(setRequestAuthParams(request));
    }

    public Request add(DiscoveryRequest request) {
        return mRequestQueue.add(setRequestAuthParams(request));
    }

    public Request add(DiscoveryXMLRPCRequest request) {
        return mRequestQueue.add(setRequestAuthParams(request));
    }

    private BaseRequest setRequestAuthParams(BaseRequest request) {
        request.setOnAuthFailedListener(mOnAuthFailedListener);
        request.setOnParseErrorListener(mOnParseErrorListener);
        request.setUserAgent(mUserAgent.getUserAgent());
        request.setHTTPAuthHeaderOnMatchingURL(mHTTPAuthManager);
        return request;
    }
}
