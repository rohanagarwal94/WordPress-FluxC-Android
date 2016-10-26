package org.wordpress.android.fluxc.media;

import android.content.Context;

import com.yarolegovich.wellsql.WellSql;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.wordpress.android.fluxc.Dispatcher;
import org.wordpress.android.fluxc.SingleStoreWellSqlConfigForTests;
import org.wordpress.android.fluxc.model.MediaModel;
import org.wordpress.android.fluxc.model.SiteModel;
import org.wordpress.android.fluxc.network.rest.wpcom.media.MediaRestClient;
import org.wordpress.android.fluxc.network.xmlrpc.media.MediaXMLRPCClient;
import org.wordpress.android.fluxc.persistence.MediaSqlUtils;
import org.wordpress.android.fluxc.persistence.WellSqlConfig;
import org.wordpress.android.fluxc.store.MediaStore;
import org.wordpress.android.fluxc.utils.MediaUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.wordpress.android.fluxc.media.MediaTestUtils.*;

@RunWith(RobolectricTestRunner.class)
public class MediaStoreTest {
    private MediaStore mMediaStore = new MediaStore(new Dispatcher(),
            Mockito.mock(MediaRestClient.class), Mockito.mock(MediaXMLRPCClient.class));

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.application.getApplicationContext();
        WellSqlConfig config = new SingleStoreWellSqlConfigForTests(context, MediaModel.class);
        WellSql.init(config);
        config.reset();
    }

    @Test
    public void testGetAllMedia() {
        final long testSiteId = 2;
        final List<MediaModel> testMedia = insertRandomMediaIntoDatabase(testSiteId, 5);

        // get all media via MediaStore
        List<MediaModel> storeMedia = mMediaStore.getAllSiteMedia(getTestSiteWithId(testSiteId));
        assertNotNull(storeMedia);
        assertEquals(testMedia.size(), storeMedia.size());

        // verify media
        for (MediaModel media : storeMedia) {
            assertEquals(testSiteId, media.getSiteId());
            assertTrue(testMedia.contains(media));
        }
    }

    @Test
    public void testMediaCount() {
        final long testSiteId = 2;
        SiteModel testSite = getTestSiteWithId(testSiteId);
        assertTrue(mMediaStore.getSiteMediaCount(testSite) == 0);

        // count after insertion
        insertRandomMediaIntoDatabase(testSiteId, 5);
        assertTrue(mMediaStore.getSiteMediaCount(testSite) == 5);

        // count after inserting with different site ID
        final long wrongSiteId = testSiteId + 1;
        SiteModel wrongSite = getTestSiteWithId(wrongSiteId);
        assertTrue(mMediaStore.getSiteMediaCount(wrongSite) == 0);
        insertRandomMediaIntoDatabase(wrongSiteId, 1);
        assertTrue(mMediaStore.getSiteMediaCount(wrongSite) == 1);
        assertTrue(mMediaStore.getSiteMediaCount(testSite) == 5);
    }

    @Test
    public void testHasSiteMediaWithId() {
        final long testSiteId = 24;
        final long testMediaId = 22;
        SiteModel testSite = getTestSiteWithId(testSiteId);
        assertTrue(mMediaStore.getSiteMediaCount(testSite) == 0);
        assertFalse(mMediaStore.hasSiteMediaWithId(testSite, testMediaId));

        // add test media
        MediaModel testMedia = getBasicMedia();
        testMedia.setSiteId(testSiteId);
        testMedia.setMediaId(testMediaId);
        assertTrue(insertMediaIntoDatabase(testMedia) == 0);

        // verify store has inserted media
        assertTrue(mMediaStore.getSiteMediaCount(testSite) == 1);
        assertTrue(mMediaStore.hasSiteMediaWithId(testSite, testMediaId));
    }

    @Test
    public void testGetSpecificSiteMedia() {
        final long testSiteId = 25;
        final long testMediaId = 11;
        SiteModel testSite = getTestSiteWithId(testSiteId);
        assertFalse(mMediaStore.hasSiteMediaWithId(testSite, testMediaId));

        // add test media
        MediaModel testMedia = getBasicMedia();
        testMedia.setSiteId(testSiteId);
        testMedia.setMediaId(testMediaId);
        assertTrue(insertMediaIntoDatabase(testMedia) == 0);

        // cannot get media with incorrect site ID
        final long wrongSiteId = testSiteId + 1;
        SiteModel wrongSite = getTestSiteWithId(wrongSiteId);
        assertNull(mMediaStore.getSiteMediaWithId(wrongSite, testMediaId));

        // verify stored media
        final MediaModel storeMedia = mMediaStore.getSiteMediaWithId(testSite, testMediaId);
        assertNotNull(storeMedia);
        assertEquals(testMedia, storeMedia);
    }

    @Test
    public void testGetListOfSiteMedia() {
        // insert list of media
        final int testListSize = 10;
        final long testSiteId = 55;
        SiteModel testSite = getTestSiteWithId(testSiteId);
        List<MediaModel> insertedMedia = insertRandomMediaIntoDatabase(testSiteId, testListSize);
        assertTrue(mMediaStore.getSiteMediaCount(testSite) == testListSize);

        // create whitelist
        List<Long> whitelist = new ArrayList<>(testListSize / 2);
        for (int i = 0; i < testListSize; i += 2) {
            whitelist.add(insertedMedia.get(i).getMediaId());
        }

        final List<MediaModel> storeMedia = mMediaStore.getSiteMediaWithIds(testSite, whitelist);
        assertNotNull(storeMedia);
        assertTrue(storeMedia.size() == whitelist.size());
        for (MediaModel media : storeMedia) {
            assertTrue(whitelist.contains(media.getMediaId()));
        }
    }

    @Test
    public void testGetSiteImages() {
        final String testVideoPath = "/test/test_video.mp4";
        final String testImagePath = "/test/test_image.jpg";
        final long testSiteId = 55;
        final long testVideoId = 987;
        final long testImageId = 654;

        // insert media of different types
        MediaModel videoMedia = generateMediaFromPath(testSiteId, testVideoId, testVideoPath);
        assertTrue(MediaUtils.isVideoMimeType(videoMedia.getMimeType()));
        MediaModel imageMedia = generateMediaFromPath(testSiteId, testImageId, testImagePath);
        assertTrue(MediaUtils.isImageMimeType(imageMedia.getMimeType()));
        insertMediaIntoDatabase(videoMedia);
        insertMediaIntoDatabase(imageMedia);

        final List<MediaModel> storeImages = mMediaStore.getSiteImages(getTestSiteWithId(testSiteId));
        assertNotNull(storeImages);
        assertTrue(storeImages.size() == 1);
        assertEquals(testImageId, storeImages.get(0).getMediaId());
        assertTrue(MediaUtils.isImageMimeType(storeImages.get(0).getMimeType()));
    }

    @Test
    public void testGetSiteImageCount() {
        final long testSiteId = 9001;
        SiteModel testSite = getTestSiteWithId(testSiteId);
        assertTrue(mMediaStore.getSiteImageCount(testSite) == 0);

        // insert both images and videos
        final int testListSize = 10;
        final List<MediaModel> testImages = new ArrayList<>(testListSize);
        final List<MediaModel> testVideos = new ArrayList<>(testListSize);
        final String testVideoPath = "/test/test_video%d.mp4";
        final String testImagePath = "/test/test_image%d.png";
        for (int i = 0; i < testListSize; ++i) {
            MediaModel testImage = generateMediaFromPath(testSiteId, i, String.format(testImagePath, i));
            MediaModel testVideo = generateMediaFromPath(testSiteId, i + testListSize, String.format(testVideoPath, i));
            assertTrue(insertMediaIntoDatabase(testImage) == 0);
            assertTrue(insertMediaIntoDatabase(testVideo) == 0);
            testImages.add(testImage);
            testVideos.add(testVideo);
        }

        assertTrue(mMediaStore.getSiteMediaCount(testSite) == testImages.size() + testVideos.size());
        assertTrue(mMediaStore.getSiteImageCount(testSite) == testImages.size());
    }

    @Test
    public void testGetSiteImagesBlacklist() {
        final long testSiteId = 3;
        SiteModel testSite = getTestSiteWithId(testSiteId);
        assertTrue(mMediaStore.getSiteImageCount(testSite) == 0);

        final int testListSize = 10;
        final List<MediaModel> testImages = new ArrayList<>(testListSize);
        final String testImagePath = "/test/test_image%d.png";
        for (int i = 0; i < testListSize; ++i) {
            MediaModel image = generateMediaFromPath(testSiteId, i, String.format(testImagePath, i));
            assertTrue(insertMediaIntoDatabase(image) == 0);
            testImages.add(image);
        }
        assertTrue(mMediaStore.getSiteImageCount(testSite) == testListSize);

        // create blacklist
        List<Long> blacklist = new ArrayList<>(testListSize / 2);
        for (int i = 0; i < testListSize; i += 2) {
            blacklist.add(testImages.get(i).getMediaId());
        }

        final List<MediaModel> storeMedia = mMediaStore.getSiteImagesExcludingIds(testSite, blacklist);
        assertNotNull(storeMedia);
        assertEquals(testListSize - blacklist.size(), storeMedia.size());
        for (MediaModel media : storeMedia) {
            assertFalse(blacklist.contains(media.getMediaId()));
        }
    }

    @Test
    public void testGetUnattachedSiteMedia() {
        final long testSiteId = 1000101110;
        final int testPoolSize = 10;
        final List<MediaModel> unattachedMedia = new ArrayList<>(testPoolSize);
        for (int i = 0; i < testPoolSize; ++i) {
            MediaModel attached = generateRandomizedMedia(testSiteId);
            MediaModel unattached = generateRandomizedMedia(testSiteId);
            attached.setMediaId(i);
            unattached.setMediaId(i + testPoolSize);
            attached.setPostId(i + testPoolSize);
            unattached.setPostId(0);
            insertMediaIntoDatabase(attached);
            insertMediaIntoDatabase(unattached);
            unattachedMedia.add(unattached);
        }

        final List<MediaModel> storeMedia = mMediaStore.getUnattachedSiteMedia(getTestSiteWithId(testSiteId));
        assertNotNull(storeMedia);
        assertTrue(storeMedia.size() == unattachedMedia.size());
        for (int i = 0; i < storeMedia.size(); ++i) {
            assertTrue(storeMedia.contains(unattachedMedia.get(i)));
        }
    }

    @Test
    public void testGetUnattachedSiteMediaCount() {
        final long testSiteId = 1000101110;
        final int testPoolSize = 10;
        for (int i = 0; i < testPoolSize; ++i) {
            MediaModel attached = generateRandomizedMedia(testSiteId);
            MediaModel unattached = generateRandomizedMedia(testSiteId);
            attached.setMediaId(i);
            unattached.setMediaId(i + testPoolSize);
            attached.setPostId(i + testPoolSize);
            unattached.setPostId(0);
            insertMediaIntoDatabase(attached);
            insertMediaIntoDatabase(unattached);
        }
        assertTrue(mMediaStore.getUnattachedSiteMediaCount(getTestSiteWithId(testSiteId)) == testPoolSize);
    }

    @Test
    public void testGetLocalSiteMedia() {
        final long testSiteId = 9;
        final long localMediaId = 2468;
        final long remoteMediaId = 1357;

        // add local media to site
        final MediaModel localMedia = getBasicMedia();
        localMedia.setSiteId(testSiteId);
        localMedia.setMediaId(localMediaId);
        localMedia.setUploadState(MediaModel.UploadState.UPLOADING.toString());
        insertMediaIntoDatabase(localMedia);

        // add remote media
        final MediaModel remoteMedia = getBasicMedia();
        remoteMedia.setSiteId(testSiteId);
        remoteMedia.setMediaId(remoteMediaId);
        // remote media has a defined upload date, simulated here
        remoteMedia.setUploadState(MediaModel.UploadState.UPLOADED.toString());
        insertMediaIntoDatabase(remoteMedia);

        SiteModel testSite = getTestSiteWithId(testSiteId);
        assertEquals(2, mMediaStore.getSiteMediaCount(testSite));

        // verify local store media
        final List<MediaModel> storeMedia = mMediaStore.getLocalSiteMedia(testSite);
        assertNotNull(storeMedia);
        assertEquals(1, storeMedia.size());
        assertNotNull(storeMedia.get(0));
        assertEquals(localMediaId, storeMedia.get(0).getMediaId());
    }

    @Test
    public void testGetUrlForVideoWithVideoPressGuid() {
        // insert video
        final long testSiteId = 13;
        final long testMediaId = 42;
        final String testVideoPath = "/test/test_video.mp4";
        final MediaModel testVideo = generateMediaFromPath(testSiteId, testMediaId, testVideoPath);
        final String testUrl = "http://notarealurl.testfluxc.org/not/a/real/resource/path.mp4";
        final String testVideoPressGuid = "thisisonlyatest";
        testVideo.setUrl(testUrl);
        testVideo.setVideoPressGuid(testVideoPressGuid);
        assertTrue(insertMediaIntoDatabase(testVideo) == 0);

        // retrieve video and verify
        final String storeUrl = mMediaStore
                .getUrlForSiteVideoWithVideoPressGuid(getTestSiteWithId(testSiteId), testVideoPressGuid);
        assertNotNull(storeUrl);
        assertEquals(testUrl, storeUrl);
    }

    @Test
    public void testGetThumbnailUrl() {
        // create and insert media with defined thumbnail URL
        final long testSiteId = 180;
        final long testMediaId = 360;
        final MediaModel testMedia = generateRandomizedMedia(testSiteId);
        final String testUrl = "http://notarealurl.testfluxc.org/not/a/real/resource/path.mp4";
        testMedia.setThumbnailUrl(testUrl);
        testMedia.setMediaId(testMediaId);
        assertTrue(insertMediaIntoDatabase(testMedia) == 0);

        // retrieve media and verify
        final String storeUrl = mMediaStore
                .getThumbnailUrlForSiteMediaWithId(getTestSiteWithId(testSiteId), testMediaId);
        assertNotNull(storeUrl);
        assertEquals(testUrl, storeUrl);
    }

    @Test
    public void testSearchSiteMediaTitles() {
        final long testSiteId = 628;
        final int testPoolSize = 10;
        final String[] testTitles = new String[testPoolSize];

        String baseString = "Base String";
        for (int i = 0; i < testPoolSize; ++i) {
            testTitles[i] = baseString;
            MediaModel testMedia = generateMedia(baseString, null, null, null);
            testMedia.setSiteId(testSiteId);
            testMedia.setMediaId(i);
            assertTrue(insertMediaIntoDatabase(testMedia) == 0);
            baseString += String.valueOf(i);
        }

        for (int i = 0; i < testPoolSize; ++i) {
            List<MediaModel> storeMedia = mMediaStore
                    .searchSiteMediaByTitle(getTestSiteWithId(testSiteId), testTitles[i]);
            assertNotNull(storeMedia);
            assertTrue(storeMedia.size() == testPoolSize - i);
        }
    }

    @Test
    public void testGetPostMedia() {
        final long testSiteId = 11235813;
        final long testPostId = 213253;
        final long postMediaId = 13;
        final long unattachedMediaId = 57;
        final long otherMediaId = 911;
        final String testPath = "this/is/only/a/test.png";

        // add post media with test path
        final MediaModel postMedia = getBasicMedia();
        postMedia.setSiteId(testSiteId);
        postMedia.setPostId(testPostId);
        postMedia.setMediaId(postMediaId);
        postMedia.setFilePath(testPath);
        insertMediaIntoDatabase(postMedia);

        // add unattached media with test path
        final MediaModel unattachedMedia = getBasicMedia();
        unattachedMedia.setSiteId(testSiteId);
        unattachedMedia.setPostId(testPostId);
        unattachedMedia.setFilePath(testPath);
        unattachedMedia.setMediaId(unattachedMediaId);
        insertMediaIntoDatabase(unattachedMedia);

        // add post media with different file path
        final MediaModel otherPathMedia = getBasicMedia();
        otherPathMedia.setSiteId(testSiteId);
        otherPathMedia.setPostId(testPostId);
        otherPathMedia.setMediaId(otherMediaId);
        otherPathMedia.setFilePath("appended/" + testPath);
        insertMediaIntoDatabase(otherPathMedia);

        // verify the correct media is in the store
        final MediaModel storeMedia = mMediaStore.getPostMediaWithPath(testPostId, testPath);
        assertNotNull(storeMedia);
        assertEquals(testPath, storeMedia.getFilePath());
        assertEquals(postMediaId, storeMedia.getMediaId());
        assertEquals(3, mMediaStore.getSiteMediaCount(getTestSiteWithId(testSiteId)));
    }

    @Test
    public void testGetNextSiteMediaToDelete() {
        final long testSiteId = 30984;
        final int count = 10;

        // add media with varying upload states
        final List<MediaModel> pendingDelete = generateRandomizedMediaList(count, testSiteId);
        final List<MediaModel> other = generateRandomizedMediaList(count, testSiteId);
        for (int i = 0; i < count; ++i) {
            pendingDelete.get(i).setUploadState(MediaModel.UploadState.DELETE.toString());
            pendingDelete.get(i).setMediaId(i + (count * 2));
            other.get(i).setUploadState(MediaModel.UploadState.UPLOADED.toString());
            other.get(i).setMediaId(i + count);
            insertMediaIntoDatabase(pendingDelete.get(i));
            insertMediaIntoDatabase(other.get(i));
        }

        SiteModel testSite = getTestSiteWithId(testSiteId);
        assertEquals(count * 2, mMediaStore.getSiteMediaCount(testSite));

        // verify store media updates as media is deleted
        for (int i = 0; i < count; ++i) {
            MediaModel next = mMediaStore.getNextSiteMediaToDelete(testSite);
            assertNotNull(next);
            assertEquals(MediaModel.UploadState.DELETE.toString(), next.getUploadState());
            assertTrue(pendingDelete.contains(next));
            MediaSqlUtils.deleteMedia(next);
            assertEquals(count * 2 - i - 1, mMediaStore.getSiteMediaCount(testSite));
            pendingDelete.remove(next);
        }
    }

    @Test
    public void testHasSiteMediaToDelete() {
        final long testSiteId = 30984;
        final int count = 10;

        // add media with varying upload states
        final List<MediaModel> pendingDelete = generateRandomizedMediaList(count, testSiteId);
        final List<MediaModel> other = generateRandomizedMediaList(count, testSiteId);
        for (int i = 0; i < count; ++i) {
            pendingDelete.get(i).setUploadState(MediaModel.UploadState.DELETE.toString());
            pendingDelete.get(i).setMediaId(i + (count * 2));
            other.get(i).setUploadState(MediaModel.UploadState.DELETED.toString());
            other.get(i).setMediaId(i + count);
            insertMediaIntoDatabase(pendingDelete.get(i));
            insertMediaIntoDatabase(other.get(i));
        }

        SiteModel testSite = getTestSiteWithId(testSiteId);
        assertEquals(count * 2, mMediaStore.getSiteMediaCount(testSite));

        // verify store still has media to delete after deleting one
        assertTrue(mMediaStore.hasSiteMediaToDelete(testSite));
        MediaModel next = mMediaStore.getNextSiteMediaToDelete(testSite);
        assertNotNull(next);
        assertTrue(pendingDelete.contains(next));
        MediaSqlUtils.deleteMedia(next);
        pendingDelete.remove(next);
        assertEquals(count * 2 - 1, mMediaStore.getSiteMediaCount(testSite));
        assertTrue(mMediaStore.hasSiteMediaToDelete(testSite));

        // verify store has no media to delete after removing all
        for (MediaModel pending : pendingDelete) {
            MediaSqlUtils.deleteMedia(pending);
        }
        assertEquals(count, mMediaStore.getSiteMediaCount(testSite));
        assertFalse(mMediaStore.hasSiteMediaToDelete(testSite));
    }

    private MediaModel getBasicMedia() {
        return generateMedia("Test Title", "Test Description", "Test Caption", "Test Alt");
    }

    private SiteModel getTestSiteWithId(long siteId) {
        SiteModel siteModel = new SiteModel();
        siteModel.setSiteId(siteId);
        return siteModel;
    }
}