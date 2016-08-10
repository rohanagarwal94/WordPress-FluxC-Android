package org.wordpress.android.fluxc.action;

import org.wordpress.android.fluxc.network.rest.wpcom.site.SiteRestClient.NewSiteResponsePayload;
import org.wordpress.android.fluxc.annotations.Action;
import org.wordpress.android.fluxc.annotations.ActionEnum;
import org.wordpress.android.fluxc.model.SiteModel;
import org.wordpress.android.fluxc.model.SitesModel;
import org.wordpress.android.fluxc.store.SiteStore.NewSitePayload;
import org.wordpress.android.fluxc.store.SiteStore.RefreshSitesXMLRPCPayload;

@ActionEnum
public enum SiteAction implements org.wordpress.android.fluxc.annotations.action.IAction {
    @Action(payloadType = SiteModel.class)
    FETCH_SITE,
    @Action
    FETCH_SITES,
    @Action(payloadType = RefreshSitesXMLRPCPayload.class)
    FETCH_SITES_XML_RPC,
    @Action(payloadType = SiteModel.class)
    UPDATE_SITE,
    @Action(payloadType = SitesModel.class)
    UPDATE_SITES,
    @Action(payloadType = SiteModel.class)
    REMOVE_SITE,
    @Action
    REMOVE_WPCOM_SITES,
    @Action(payloadType = SitesModel.class)
    SHOW_SITES,
    @Action(payloadType = SitesModel.class)
    HIDE_SITES,
    @Action(payloadType = NewSitePayload.class)
    CREATE_NEW_SITE,
    @Action(payloadType = NewSiteResponsePayload.class)
    CREATED_NEW_SITE,
}