package org.wordpress.android.fluxc.store;

import android.support.annotation.NonNull;

import com.wellsql.generated.MediaModelTable;
import com.yarolegovich.wellsql.WellCursor;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.wordpress.android.fluxc.Dispatcher;
import org.wordpress.android.fluxc.Payload;
import org.wordpress.android.fluxc.action.MediaAction;
import org.wordpress.android.fluxc.annotations.action.Action;
import org.wordpress.android.fluxc.annotations.action.IAction;
import org.wordpress.android.fluxc.model.MediaModel;
import org.wordpress.android.fluxc.model.MediaModel.UploadState;
import org.wordpress.android.fluxc.model.SiteModel;
import org.wordpress.android.fluxc.network.BaseRequest;
import org.wordpress.android.fluxc.network.BaseUploadRequestBody;
import org.wordpress.android.fluxc.network.rest.wpcom.media.MediaRestClient;
import org.wordpress.android.fluxc.network.xmlrpc.media.MediaXMLRPCClient;
import org.wordpress.android.fluxc.persistence.MediaSqlUtils;
import org.wordpress.android.util.AppLog;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class MediaStore extends Store {
    public static final int NUM_MEDIA_PER_FETCH = 50;

    //
    // Payloads
    //

    /**
     * Actions: FETCH(ED)_MEDIA, PUSH(ED)_MEDIA, UPLOAD(ED)_MEDIA, DELETE(D)_MEDIA, UPDATE_MEDIA, and REMOVE_MEDIA
     */
    public static class MediaPayload extends Payload {
        public SiteModel site;
        public MediaError error;
        public MediaModel media;
        public MediaPayload(SiteModel site, MediaModel media) {
            this(site, media, null);
        }
        public MediaPayload(SiteModel site, MediaModel media, MediaError error) {
            this.site = site;
            this.media = media;
            this.error = error;
        }
    }

    /**
     * Actions: FETCH_MEDIA_LIST
     */
    public static class FetchMediaListPayload extends Payload {
        public SiteModel site;
        public boolean loadMore;

        public FetchMediaListPayload(SiteModel site) {
            this.site = site;
        }

        public FetchMediaListPayload(SiteModel site, boolean loadMore) {
            this.site = site;
            this.loadMore = loadMore;
        }
    }

    /**
     * Actions: FETCHED_MEDIA_LIST
     */
    public static class FetchMediaListResponsePayload extends Payload {
        public SiteModel site;
        public MediaError error;
        public List<MediaModel> mediaList;
        public boolean loadedMore;
        public boolean canLoadMore;
        public FetchMediaListResponsePayload(SiteModel site, @NonNull List<MediaModel> mediaList, boolean loadedMore,
                                             boolean canLoadMore) {
            this.site = site;
            this.mediaList = mediaList;
            this.loadedMore = loadedMore;
            this.canLoadMore = canLoadMore;
        }

        public FetchMediaListResponsePayload(SiteModel site, MediaError error) {
            this.mediaList = new ArrayList<>();
            this.site = site;
            this.error = error;
        }
    }

    /**
     * Actions: UPLOADED_MEDIA, CANCELED_MEDIA_UPLOAD
     */
    public static class ProgressPayload extends Payload {
        public MediaModel media;
        public float progress;
        public boolean completed;
        public boolean canceled;
        public MediaError error;
        public ProgressPayload(MediaModel media, float progress, boolean completed, boolean canceled) {
            this(media, progress, completed, null);
            this.canceled = canceled;
        }
        public ProgressPayload(MediaModel media, float progress, boolean completed, MediaError error) {
            this.media = media;
            this.progress = progress;
            this.completed = completed;
            this.error = error;
        }
    }

    //
    // OnChanged events
    //

    public static class MediaError implements OnChangedError {
        public MediaErrorType type;
        public String message;
        public MediaError(MediaErrorType type) {
            this.type = type;
        }
        public MediaError(MediaErrorType type, String message) {
            this.type = type;
            this.message = message;
        }
    }

    public static class OnMediaChanged extends OnChanged<MediaError> {
        public MediaAction cause;
        public List<MediaModel> mediaList;
        public OnMediaChanged(MediaAction cause) {
            this(cause, new ArrayList<MediaModel>(), null);
        }
        public OnMediaChanged(MediaAction cause, @NonNull List<MediaModel> mediaList) {
            this(cause, mediaList, null);
        }
        public OnMediaChanged(MediaAction cause, MediaError error) {
            this(cause, new ArrayList<MediaModel>(), error);
        }
        public OnMediaChanged(MediaAction cause, @NonNull List<MediaModel> mediaList, MediaError error) {
            this.cause = cause;
            this.mediaList = mediaList;
            this.error = error;
        }
    }

    public static class OnMediaListFetched extends OnChanged<MediaError> {
        public SiteModel site;
        public boolean canLoadMore;
        public OnMediaListFetched(SiteModel site, boolean canLoadMore) {
            this.site = site;
            this.canLoadMore = canLoadMore;
        }
        public OnMediaListFetched(SiteModel site, MediaError error) {
            this.site = site;
            this.error = error;
        }
    }

    public static class OnMediaUploaded extends OnChanged<MediaError> {
        public MediaModel media;
        public float progress;
        public boolean completed;
        public boolean canceled;
        public OnMediaUploaded(MediaModel media, float progress, boolean completed, boolean canceled) {
            this.media = media;
            this.progress = progress;
            this.completed = completed;
            this.canceled = canceled;
        }
    }

    //
    // Errors
    //

    public enum MediaErrorType {
        // local errors, occur before sending network requests
        FS_READ_PERMISSION_DENIED,
        NULL_MEDIA_ARG,
        MALFORMED_MEDIA_ARG,
        DB_QUERY_FAILURE,

        // network errors, occur in response to network requests
        NOT_FOUND,
        AUTHORIZATION_REQUIRED,
        PARSE_ERROR,
        NOT_AUTHENTICATED,
        REQUEST_TOO_LARGE,

        // unknown/unspecified
        GENERIC_ERROR;

        public static MediaErrorType fromBaseNetworkError(BaseRequest.BaseNetworkError baseError) {
            switch (baseError.type) {
                case NOT_FOUND:
                    return MediaErrorType.NOT_FOUND;
                case NOT_AUTHENTICATED:
                    return MediaErrorType.NOT_AUTHENTICATED;
                case AUTHORIZATION_REQUIRED:
                    return MediaErrorType.AUTHORIZATION_REQUIRED;
                case PARSE_ERROR:
                    return MediaErrorType.PARSE_ERROR;
                default:
                    return MediaErrorType.GENERIC_ERROR;
            }
        }

        public static MediaErrorType fromHttpStatusCode(int code) {
            switch (code) {
                case 404:
                    return MediaErrorType.NOT_FOUND;
                case 403:
                    return MediaErrorType.NOT_AUTHENTICATED;
                case 413:
                    return MediaErrorType.REQUEST_TOO_LARGE;
                default:
                    return MediaErrorType.GENERIC_ERROR;
            }
        }
    }

    private MediaRestClient mMediaRestClient;
    private MediaXMLRPCClient mMediaXmlrpcClient;

    @Inject
    public MediaStore(Dispatcher dispatcher, MediaRestClient restClient, MediaXMLRPCClient xmlrpcClient) {
        super(dispatcher);
        mMediaRestClient = restClient;
        mMediaXmlrpcClient = xmlrpcClient;
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    @Override
    public void onAction(Action action) {
        IAction actionType = action.getType();
        if (!(actionType instanceof MediaAction)) {
            return;
        }

        switch ((MediaAction) actionType) {
            case PUSH_MEDIA:
                performPushMedia((MediaPayload) action.getPayload());
                break;
            case UPLOAD_MEDIA:
                performUploadMedia((MediaPayload) action.getPayload());
                break;
            case FETCH_MEDIA_LIST:
                performFetchMediaList((FetchMediaListPayload) action.getPayload());
                break;
            case FETCH_MEDIA:
                performFetchMedia((MediaPayload) action.getPayload());
                break;
            case DELETE_MEDIA:
                performDeleteMedia((MediaPayload) action.getPayload());
                break;
            case CANCEL_MEDIA_UPLOAD:
                performCancelUpload((MediaPayload) action.getPayload());
                break;
            case PUSHED_MEDIA:
                handleMediaPushed((MediaPayload) action.getPayload());
                break;
            case UPLOADED_MEDIA:
                handleMediaUploaded((ProgressPayload) action.getPayload());
                break;
            case FETCHED_MEDIA_LIST:
                handleMediaListFetched((FetchMediaListResponsePayload) action.getPayload());
                break;
            case FETCHED_MEDIA:
                handleMediaFetched((MediaPayload) action.getPayload());
                break;
            case DELETED_MEDIA:
                handleMediaDeleted((MediaPayload) action.getPayload());
                break;
            case CANCELED_MEDIA_UPLOAD:
                handleMediaUploaded((ProgressPayload) action.getPayload());
                break;
            case UPDATE_MEDIA:
                updateMedia(((MediaModel) action.getPayload()), true);
                break;
            case REMOVE_MEDIA:
                removeMedia(((MediaModel) action.getPayload()));
                break;
            case REMOVE_ALL_MEDIA:
                removeAllMedia();
                break;
        }
    }

    @Override
    public void onRegister() {
        AppLog.d(AppLog.T.MEDIA, "MediaStore onRegister");
    }

    //
    // Getters
    //

    public MediaModel instantiateMediaModel() {
        MediaModel media = new MediaModel();

        media = MediaSqlUtils.insertMediaForResult(media);

        if (media.getId() == -1) {
            media = null;
        }

        return media;
    }

    public List<MediaModel> getAllSiteMedia(SiteModel siteModel) {
        return MediaSqlUtils.getAllSiteMedia(siteModel);
    }

    public WellCursor<MediaModel> getAllSiteMediaAsCursor(SiteModel siteModel) {
        return MediaSqlUtils.getAllSiteMediaAsCursor(siteModel);
    }

    public static final List<String> NOT_DELETED_STATES = new ArrayList<>();
    static {
        NOT_DELETED_STATES.add(UploadState.DELETE.toString());
        NOT_DELETED_STATES.add(UploadState.FAILED.toString());
        NOT_DELETED_STATES.add(UploadState.QUEUED.toString());
        NOT_DELETED_STATES.add(UploadState.UPLOADED.toString());
        NOT_DELETED_STATES.add(UploadState.UPLOADING.toString());
    }

    public WellCursor<MediaModel> getNotDeletedSiteMediaAsCursor(SiteModel site) {
        return MediaSqlUtils.getMediaWithStatesAsCursor(site, NOT_DELETED_STATES);
    }

    public WellCursor<MediaModel> getNotDeletedSiteImagesAsCursor(SiteModel site) {
        return MediaSqlUtils.getImagesWithStatesAsCursor(site, NOT_DELETED_STATES);
    }

    public WellCursor<MediaModel> getNotDeletedUnattachedMediaAsCursor(SiteModel site) {
        return MediaSqlUtils.getUnattachedMediaWithStates(site, NOT_DELETED_STATES);
    }

    public int getSiteMediaCount(SiteModel siteModel) {
        return getAllSiteMedia(siteModel).size();
    }

    public boolean hasSiteMediaWithId(SiteModel siteModel, long mediaId) {
        return getSiteMediaWithId(siteModel, mediaId) != null;
    }

    public MediaModel getSiteMediaWithId(SiteModel siteModel, long mediaId) {
        List<MediaModel> media = MediaSqlUtils.getSiteMediaWithId(siteModel, mediaId);
        return media.size() > 0 ? media.get(0) : null;
    }

    public MediaModel getMediaWithLocalId(int localMediaId) {
        return MediaSqlUtils.getMediaWithLocalId(localMediaId);
    }

    public List<MediaModel> getSiteMediaWithIds(SiteModel siteModel, List<Long> mediaIds) {
        return MediaSqlUtils.getSiteMediaWithIds(siteModel, mediaIds);
    }

    public WellCursor<MediaModel> getSiteMediaWithIdsAsCursor(SiteModel siteModel, List<Long> mediaIds) {
        return MediaSqlUtils.getSiteMediaWithIdsAsCursor(siteModel, mediaIds);
    }

    public List<MediaModel> getSiteImages(SiteModel siteModel) {
        return MediaSqlUtils.getSiteImages(siteModel);
    }

    public WellCursor<MediaModel> getSiteImagesAsCursor(SiteModel siteModel) {
        return MediaSqlUtils.getSiteImagesAsCursor(siteModel);
    }

    public int getSiteImageCount(SiteModel siteModel) {
        return getSiteImages(siteModel).size();
    }

    public List<MediaModel> getSiteImagesExcludingIds(SiteModel siteModel, List<Long> filter) {
        return MediaSqlUtils.getSiteImagesExcluding(siteModel, filter);
    }

    public WellCursor<MediaModel> getSiteImagesExcludingIdsAsCursor(SiteModel siteModel, List<Long> filter) {
        return MediaSqlUtils.getSiteImagesExcludingAsCursor(siteModel, filter);
    }

    public List<MediaModel> getUnattachedSiteMedia(SiteModel siteModel) {
        return MediaSqlUtils.matchSiteMedia(siteModel, MediaModelTable.POST_ID, 0);
    }

    public WellCursor<MediaModel> getUnattachedSiteMediaAsCursor(SiteModel siteModel) {
        return MediaSqlUtils.matchSiteMediaAsCursor(siteModel, MediaModelTable.POST_ID, 0);
    }

    public int getUnattachedSiteMediaCount(SiteModel siteModel) {
        return getUnattachedSiteMedia(siteModel).size();
    }

    public List<MediaModel> getLocalSiteMedia(SiteModel siteModel) {
        UploadState expectedState = UploadState.UPLOADED;
        return MediaSqlUtils.getSiteMediaExcluding(siteModel, MediaModelTable.UPLOAD_STATE, expectedState);
    }

    public String getUrlForSiteVideoWithVideoPressGuid(SiteModel siteModel, String videoPressGuid) {
        List<MediaModel> media =
                MediaSqlUtils.matchSiteMedia(siteModel, MediaModelTable.VIDEO_PRESS_GUID, videoPressGuid);
        return media.size() > 0 ? media.get(0).getUrl() : null;
    }

    public String getThumbnailUrlForSiteMediaWithId(SiteModel siteModel, long mediaId) {
        List<MediaModel> media = MediaSqlUtils.getSiteMediaWithId(siteModel, mediaId);
        return media.size() > 0 ? media.get(0).getThumbnailUrl() : null;
    }

    public List<MediaModel> searchSiteMediaByTitle(SiteModel siteModel, String titleSearch) {
        return MediaSqlUtils.searchSiteMedia(siteModel, MediaModelTable.TITLE, titleSearch);
    }

    public WellCursor<MediaModel> searchSiteMediaByTitleAsCursor(SiteModel siteModel, String titleSearch) {
        return MediaSqlUtils.searchSiteMediaAsCursor(siteModel, MediaModelTable.TITLE, titleSearch);
    }

    public MediaModel getPostMediaWithPath(long postId, String filePath) {
        List<MediaModel> media = MediaSqlUtils.matchPostMedia(postId, MediaModelTable.FILE_PATH, filePath);
        return media.size() > 0 ? media.get(0) : null;
    }

    public MediaModel getNextSiteMediaToDelete(SiteModel siteModel) {
        List<MediaModel> media = MediaSqlUtils.matchSiteMedia(siteModel,
                MediaModelTable.UPLOAD_STATE, UploadState.DELETE.toString());
        return media.size() > 0 ? media.get(0) : null;
    }

    public boolean hasSiteMediaToDelete(SiteModel siteModel) {
        return getNextSiteMediaToDelete(siteModel) != null;
    }

    private void removeAllMedia() {
        MediaSqlUtils.deleteAllMedia();
        OnMediaChanged event = new OnMediaChanged(MediaAction.REMOVE_ALL_MEDIA);
        emitChange(event);
    }

    //
    // Action implementations
    //

    private void updateMedia(MediaModel media, boolean emit) {
        OnMediaChanged event = new OnMediaChanged(MediaAction.UPDATE_MEDIA);

        if (media == null) {
            event.error = new MediaError(MediaErrorType.NULL_MEDIA_ARG);
        } else if (MediaSqlUtils.insertOrUpdateMedia(media) > 0) {
            event.mediaList.add(media);
        } else {
            event.error = new MediaError(MediaErrorType.DB_QUERY_FAILURE);
        }

        if (emit) {
            emitChange(event);
        }
    }

    private void removeMedia(MediaModel media) {
        OnMediaChanged event = new OnMediaChanged(MediaAction.REMOVE_MEDIA);

        if (media == null) {
            event.error = new MediaError(MediaErrorType.NULL_MEDIA_ARG);
        } else if (MediaSqlUtils.deleteMedia(media) > 0) {
            event.mediaList.add(media);
        } else {
            event.error = new MediaError(MediaErrorType.DB_QUERY_FAILURE);
        }
        emitChange(event);
    }

    //
    // Helper methods that choose the appropriate network client to perform an action
    //

    private void performPushMedia(MediaPayload payload) {
        if (payload.media == null) {
            // null or empty media list -or- list contains a null value
            notifyMediaError(MediaErrorType.NULL_MEDIA_ARG, MediaAction.PUSH_MEDIA, null);
            return;
        } else if (payload.media.getMediaId() <= 0) {
            // need media ID to push changes
            notifyMediaError(MediaErrorType.MALFORMED_MEDIA_ARG, MediaAction.PUSH_MEDIA, payload.media);
            return;
        }

        if (payload.site.isUsingWpComRestApi()) {
            mMediaRestClient.pushMedia(payload.site, payload.media);
        } else {
            mMediaXmlrpcClient.pushMedia(payload.site, payload.media);
        }
    }

    private void notifyMediaUploadError(MediaErrorType errorType, String errorMessage, MediaModel media) {
        OnMediaUploaded onMediaUploaded = new OnMediaUploaded(media, 1, true, false);
        onMediaUploaded.error = new MediaError(errorType, errorMessage);
        emitChange(onMediaUploaded);
    }

    private void performUploadMedia(MediaPayload payload) {
        String errorMessage = isWellFormedForUpload(payload.media);
        if (errorMessage != null) {
            notifyMediaUploadError(MediaErrorType.MALFORMED_MEDIA_ARG, errorMessage, payload.media);
            return;
        }

        if (payload.site.isUsingWpComRestApi()) {
            mMediaRestClient.uploadMedia(payload.site, payload.media);
        } else {
            mMediaXmlrpcClient.uploadMedia(payload.site, payload.media);
        }
    }

    private void performFetchMediaList(FetchMediaListPayload payload) {
        int offset = 0;
        if (payload.loadMore) {
            List<String> list = new ArrayList<>();
            list.add(UploadState.UPLOADED.toString());
            offset = MediaSqlUtils.getMediaWithStates(payload.site, list).size();
        }
        if (payload.site.isUsingWpComRestApi()) {
            mMediaRestClient.fetchMediaList(payload.site, offset);
        } else {
            mMediaXmlrpcClient.fetchMediaList(payload.site, offset);
        }
    }

    private void performFetchMedia(MediaPayload payload) {
        if (payload.site == null || payload.media == null) {
            // null or empty media list -or- list contains a null value
            notifyMediaError(MediaErrorType.NULL_MEDIA_ARG, MediaAction.FETCH_MEDIA, payload.media);
            return;
        }

        if (payload.site.isUsingWpComRestApi()) {
            mMediaRestClient.fetchMedia(payload.site, payload.media);
        } else {
            mMediaXmlrpcClient.fetchMedia(payload.site, payload.media);
        }
    }

    private void performDeleteMedia(@NonNull MediaPayload payload) {
        if (payload.media == null) {
            notifyMediaError(MediaErrorType.NULL_MEDIA_ARG, MediaAction.DELETE_MEDIA, null);
            return;
        }

        if (payload.site.isUsingWpComRestApi()) {
            mMediaRestClient.deleteMedia(payload.site, payload.media);
        } else {
            mMediaXmlrpcClient.deleteMedia(payload.site, payload.media);
        }
    }

    private void performCancelUpload(@NonNull MediaPayload payload) {
        if (payload.media != null) {
            if (payload.site.isUsingWpComRestApi()) {
                mMediaRestClient.cancelUpload(payload.media);
            } else {
                mMediaXmlrpcClient.cancelUpload(payload.media);
            }
        }
    }

    private void handleMediaPushed(@NonNull MediaPayload payload) {
        OnMediaChanged onMediaChanged = new OnMediaChanged(MediaAction.PUSH_MEDIA, payload.error);
        if (payload.media != null) {
            updateMedia(payload.media, false);
            onMediaChanged.mediaList = new ArrayList<>();
            onMediaChanged.mediaList.add(payload.media);
        }
        emitChange(onMediaChanged);
    }

    private void handleMediaUploaded(@NonNull ProgressPayload payload) {
        if (!payload.isError() && payload.completed) {
            updateMedia(payload.media, false);
        }
        OnMediaUploaded onMediaUploaded =
                new OnMediaUploaded(payload.media, payload.progress, payload.completed, payload.canceled);
        onMediaUploaded.error = payload.error;
        if (payload.media != null) {
            MediaSqlUtils.insertOrUpdateMedia(payload.media);
        }
        emitChange(onMediaUploaded);
    }

    private void handleMediaListFetched(@NonNull FetchMediaListResponsePayload payload) {
        OnMediaListFetched onMediaListFetched;

        if (payload.isError()) {
            onMediaListFetched = new OnMediaListFetched(payload.site, payload.error);
        } else {
            // Clear existing media if this is a fresh fetch (loadMore = false in the original request)
            // This is the simplest way of keeping our local media in sync with remote media (in case of deletions)
            if (!payload.loadedMore) {
                MediaSqlUtils.deleteAllUploadedSiteMedia(payload.site);
            }
            if (!payload.mediaList.isEmpty()) {
                for (MediaModel media : payload.mediaList) {
                    updateMedia(media, false);
                }
            }
            onMediaListFetched = new OnMediaListFetched(payload.site, payload.canLoadMore);
        }

        emitChange(onMediaListFetched);
    }

    private void handleMediaFetched(@NonNull MediaPayload payload) {
        OnMediaChanged onMediaChanged = new OnMediaChanged(MediaAction.FETCH_MEDIA, payload.error);
        if (payload.media != null) {
            MediaSqlUtils.insertOrUpdateMedia(payload.media);
            onMediaChanged.mediaList = new ArrayList<>();
            onMediaChanged.mediaList.add(payload.media);
        }
        emitChange(onMediaChanged);
    }

    private void handleMediaDeleted(@NonNull MediaPayload payload) {
        OnMediaChanged onMediaChanged = new OnMediaChanged(MediaAction.DELETE_MEDIA, payload.error);
        if (payload.media != null) {
            MediaSqlUtils.deleteMedia(payload.media);
            onMediaChanged.mediaList = new ArrayList<>();
            onMediaChanged.mediaList.add(payload.media);
        }
        emitChange(onMediaChanged);
    }

    private String isWellFormedForUpload(@NonNull MediaModel media) {
        String error = BaseUploadRequestBody.hasRequiredData(media);
        if (error != null) {
            AppLog.e(AppLog.T.MEDIA, "Media doesn't have required data: " + error);
        }
        return error;
    }

    private void notifyMediaError(MediaErrorType errorType, String errorMessage, MediaAction cause,
                                  List<MediaModel> media) {
        OnMediaChanged mediaChange = new OnMediaChanged(cause, media);
        mediaChange.error = new MediaError(errorType, errorMessage);
        emitChange(mediaChange);
    }

    private void notifyMediaError(MediaErrorType errorType, MediaAction cause, MediaModel media) {
        notifyMediaError(errorType, null, cause, media);
    }

    private void notifyMediaError(MediaErrorType errorType, String errorMessage, MediaAction cause, MediaModel media) {
        List<MediaModel> mediaList = new ArrayList<>();
        mediaList.add(media);
        notifyMediaError(errorType, errorMessage, cause, mediaList);
    }
}
