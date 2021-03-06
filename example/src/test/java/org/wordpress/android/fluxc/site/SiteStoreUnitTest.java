package org.wordpress.android.fluxc.site;

import android.content.Context;
import android.database.sqlite.SQLiteException;

import com.wellsql.generated.SiteModelTable;
import com.yarolegovich.wellsql.WellSql;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.wordpress.android.fluxc.Dispatcher;
import org.wordpress.android.fluxc.model.PostFormatModel;
import org.wordpress.android.fluxc.model.SiteModel;
import org.wordpress.android.fluxc.model.SitesModel;
import org.wordpress.android.fluxc.network.rest.wpcom.site.SiteRestClient;
import org.wordpress.android.fluxc.network.xmlrpc.site.SiteXMLRPCClient;
import org.wordpress.android.fluxc.persistence.SiteSqlUtils;
import org.wordpress.android.fluxc.persistence.SiteSqlUtils.DuplicateSiteException;
import org.wordpress.android.fluxc.persistence.WellSqlConfig;
import org.wordpress.android.fluxc.store.SiteStore;
import org.wordpress.android.fluxc.store.SiteStore.UpdateSitesResult;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.wordpress.android.fluxc.utils.SiteUtils.generateJetpackSiteOverRestOnly;
import static org.wordpress.android.fluxc.utils.SiteUtils.generateJetpackSiteOverXMLRPC;
import static org.wordpress.android.fluxc.utils.SiteUtils.generatePostFormats;
import static org.wordpress.android.fluxc.utils.SiteUtils.generateSelfHostedNonJPSite;
import static org.wordpress.android.fluxc.utils.SiteUtils.generateSelfHostedSiteFutureJetpack;
import static org.wordpress.android.fluxc.utils.SiteUtils.generateTestSite;
import static org.wordpress.android.fluxc.utils.SiteUtils.generateWPComSite;

@RunWith(RobolectricTestRunner.class)
public class SiteStoreUnitTest {
    private SiteStore mSiteStore = new SiteStore(new Dispatcher(), Mockito.mock(SiteRestClient.class),
            Mockito.mock(SiteXMLRPCClient.class));

    @Before
    public void setUp() {
        Context appContext = RuntimeEnvironment.application.getApplicationContext();

        WellSqlConfig config = new WellSqlConfig(appContext);
        WellSql.init(config);
        config.reset();
    }

    @Test
    public void testSimpleInsertionAndRetrieval() {
        SiteModel siteModel = new SiteModel();
        siteModel.setSiteId(42);
        WellSql.insert(siteModel).execute();

        assertEquals(1, mSiteStore.getSitesCount());

        assertEquals(42, mSiteStore.getSites().get(0).getSiteId());
    }

    @Test
    public void testInsertOrUpdateSite() throws DuplicateSiteException {
        SiteModel site = generateWPComSite();
        SiteSqlUtils.insertOrUpdateSite(site);

        assertTrue(mSiteStore.hasSiteWithLocalId(site.getId()));
        assertEquals(site.getSiteId(), mSiteStore.getSiteByLocalId(site.getId()).getSiteId());
    }

    @Test
    public void testHasSiteAndgetCountMethods() throws DuplicateSiteException {
        assertFalse(mSiteStore.hasSite());
        assertTrue(mSiteStore.getSites().isEmpty());

        // Test counts with .COM site
        SiteModel wpComSite = generateWPComSite();
        SiteSqlUtils.insertOrUpdateSite(wpComSite);

        assertTrue(mSiteStore.hasSite());
        assertTrue(mSiteStore.hasWPComSite());
        assertFalse(mSiteStore.hasSelfHostedSite());
        assertFalse(mSiteStore.hasJetpackSite());

        assertEquals(1, mSiteStore.getSitesCount());
        assertEquals(1, mSiteStore.getWPComSitesCount());

        // Test counts with one .COM and one self-hosted site
        SiteModel selfHostedSite = generateSelfHostedNonJPSite();
        SiteSqlUtils.insertOrUpdateSite(selfHostedSite);

        assertTrue(mSiteStore.hasSite());
        assertTrue(mSiteStore.hasWPComSite());
        assertTrue(mSiteStore.hasSelfHostedSite());
        assertFalse(mSiteStore.hasJetpackSite());

        assertEquals(2, mSiteStore.getSitesCount());
        assertEquals(1, mSiteStore.getWPComSitesCount());
        assertEquals(1, mSiteStore.getSelfHostedSitesCount());
        assertEquals(1, mSiteStore.getWPComAndJetpackSitesCount());

        // Test counts with one .COM, one self-hosted and one Jetpack site
        SiteModel jetpackSiteOverXMLRPC = generateJetpackSiteOverXMLRPC();
        SiteSqlUtils.insertOrUpdateSite(jetpackSiteOverXMLRPC);

        assertTrue(mSiteStore.hasSite());
        assertTrue(mSiteStore.hasWPComSite());
        assertTrue(mSiteStore.hasSelfHostedSite());
        assertTrue(mSiteStore.hasJetpackSite());

        assertEquals(3, mSiteStore.getSitesCount());
        assertEquals(1, mSiteStore.getWPComSitesCount());
        assertEquals(1, mSiteStore.getSelfHostedSitesCount());
        assertEquals(1, mSiteStore.getJetpackSitesCount());
        assertEquals(2, mSiteStore.getWPComAndJetpackSitesCount());
    }

    @Test
    public void testHasSiteWithSiteIdAndXmlRpcUrl() throws DuplicateSiteException {
        assertFalse(mSiteStore.hasSelfHostedSiteWithSiteIdAndXmlRpcUrl(124, ""));

        SiteModel selfHostedSite = generateSelfHostedNonJPSite();
        SiteSqlUtils.insertOrUpdateSite(selfHostedSite);

        assertTrue(mSiteStore.hasSelfHostedSiteWithSiteIdAndXmlRpcUrl(selfHostedSite.getSelfHostedSiteId(),
                selfHostedSite.getXmlRpcUrl()));

        SiteModel jetpackSite = generateJetpackSiteOverXMLRPC();
        SiteSqlUtils.insertOrUpdateSite(jetpackSite);

        assertTrue(mSiteStore.hasSelfHostedSiteWithSiteIdAndXmlRpcUrl(jetpackSite.getSelfHostedSiteId(),
                jetpackSite.getXmlRpcUrl()));
    }

    @Test
    public void testHasWPComOrJetpackSiteWithSiteId() throws DuplicateSiteException {
        assertFalse(mSiteStore.hasWPComOrJetpackSiteWithSiteId(673));

        SiteModel wpComSite = generateWPComSite();
        SiteSqlUtils.insertOrUpdateSite(wpComSite);

        assertTrue(mSiteStore.hasWPComOrJetpackSiteWithSiteId(wpComSite.getSiteId()));

        SiteModel jetpackSite = generateJetpackSiteOverXMLRPC();
        SiteSqlUtils.insertOrUpdateSite(jetpackSite);

        // hasWPComOrJetpackSiteWithSiteId() should be able to locate a Jetpack site with either the site id or the
        // .COM site id
        assertTrue(mSiteStore.hasWPComOrJetpackSiteWithSiteId(jetpackSite.getSiteId()));
        assertTrue(mSiteStore.hasWPComOrJetpackSiteWithSiteId(jetpackSite.getSelfHostedSiteId()));
    }

    @Test
    public void testWPComSiteVisibility() throws DuplicateSiteException {
        // Should not cause any errors
        mSiteStore.isWPComSiteVisibleByLocalId(45);
        SiteSqlUtils.setSiteVisibility(null, true);

        SiteModel selfHostedNonJPSite = generateSelfHostedNonJPSite();
        SiteSqlUtils.insertOrUpdateSite(selfHostedNonJPSite);

        // Attempt to use with id of self-hosted site
        SiteSqlUtils.setSiteVisibility(selfHostedNonJPSite, false);
        // The self-hosted site should not be affected
        assertTrue(mSiteStore.getSiteByLocalId(selfHostedNonJPSite.getId()).isVisible());


        SiteModel wpComSite = generateWPComSite();
        SiteSqlUtils.insertOrUpdateSite(wpComSite);

        // Attempt to use with legitimate .com site
        SiteSqlUtils.setSiteVisibility(selfHostedNonJPSite, false);
        assertFalse(mSiteStore.getSiteByLocalId(wpComSite.getId()).isVisible());
        assertFalse(mSiteStore.isWPComSiteVisibleByLocalId(wpComSite.getId()));
    }

    @Test
    public void testSetAllWPComSitesVisibility() throws DuplicateSiteException {
        SiteModel selfHostedNonJPSite = generateSelfHostedNonJPSite();
        SiteSqlUtils.insertOrUpdateSite(selfHostedNonJPSite);

        // Attempt to use with id of self-hosted site
        for (SiteModel site : mSiteStore.getWPComSites()) {
            SiteSqlUtils.setSiteVisibility(site, false);
        }
        // The self-hosted site should not be affected
        assertTrue(mSiteStore.getSiteByLocalId(selfHostedNonJPSite.getId()).isVisible());

        SiteModel wpComSite1 = generateWPComSite();
        SiteModel wpComSite2 = generateWPComSite();
        wpComSite2.setId(44);
        wpComSite2.setSiteId(284);

        SiteSqlUtils.insertOrUpdateSite(wpComSite1);
        SiteSqlUtils.insertOrUpdateSite(wpComSite2);

        // Attempt to use with legitimate .com site
        for (SiteModel site : mSiteStore.getWPComSites()) {
            SiteSqlUtils.setSiteVisibility(site, false);
        }
        assertTrue(mSiteStore.getSiteByLocalId(selfHostedNonJPSite.getId()).isVisible());
        assertFalse(mSiteStore.getSiteByLocalId(wpComSite1.getId()).isVisible());
        assertFalse(mSiteStore.getSiteByLocalId(wpComSite2.getId()).isVisible());
    }

    @Test
    public void testGetIdForIdMethods() throws DuplicateSiteException {
        assertEquals(0, mSiteStore.getLocalIdForRemoteSiteId(555));
        assertEquals(0, mSiteStore.getLocalIdForSelfHostedSiteIdAndXmlRpcUrl(2626, ""));
        assertEquals(0, mSiteStore.getSiteIdForLocalId(5577));

        SiteModel selfHostedSite = generateSelfHostedNonJPSite();
        SiteModel wpComSite = generateWPComSite();
        SiteModel jetpackSite = generateJetpackSiteOverXMLRPC();
        SiteSqlUtils.insertOrUpdateSite(selfHostedSite);
        SiteSqlUtils.insertOrUpdateSite(wpComSite);
        SiteSqlUtils.insertOrUpdateSite(jetpackSite);

        assertEquals(selfHostedSite.getId(),
                mSiteStore.getLocalIdForRemoteSiteId(selfHostedSite.getSelfHostedSiteId()));
        assertEquals(wpComSite.getId(), mSiteStore.getLocalIdForRemoteSiteId(wpComSite.getSiteId()));

        // Should be able to look up a Jetpack site by .com and by .org id (assuming it's been set)
        assertEquals(jetpackSite.getId(), mSiteStore.getLocalIdForRemoteSiteId(jetpackSite.getSiteId()));
        assertEquals(jetpackSite.getId(), mSiteStore.getLocalIdForRemoteSiteId(jetpackSite.getSelfHostedSiteId()));

        assertEquals(selfHostedSite.getId(), mSiteStore.getLocalIdForSelfHostedSiteIdAndXmlRpcUrl(
                selfHostedSite.getSelfHostedSiteId(), selfHostedSite.getXmlRpcUrl()));
        assertEquals(jetpackSite.getId(), mSiteStore.getLocalIdForSelfHostedSiteIdAndXmlRpcUrl(
                jetpackSite.getSelfHostedSiteId(), jetpackSite.getXmlRpcUrl()));

        assertEquals(selfHostedSite.getSelfHostedSiteId(), mSiteStore.getSiteIdForLocalId(selfHostedSite.getId()));
        assertEquals(wpComSite.getSiteId(), mSiteStore.getSiteIdForLocalId(wpComSite.getId()));
        assertEquals(jetpackSite.getSiteId(), mSiteStore.getSiteIdForLocalId(jetpackSite.getId()));
    }

    @Test
    public void testGetSiteBySiteId() throws DuplicateSiteException {
        assertNull(mSiteStore.getSiteBySiteId(555));

        SiteModel selfHostedSite = generateSelfHostedNonJPSite();
        SiteModel wpComSite = generateWPComSite();
        SiteModel jetpackSite = generateJetpackSiteOverXMLRPC();
        SiteSqlUtils.insertOrUpdateSite(selfHostedSite);
        SiteSqlUtils.insertOrUpdateSite(wpComSite);
        SiteSqlUtils.insertOrUpdateSite(jetpackSite);

        assertEquals(2, SiteSqlUtils.getWPComAndJetpackSites().getAsCursor().getCount());
        assertNotNull(mSiteStore.getSiteBySiteId(wpComSite.getSiteId()));
        assertNotNull(mSiteStore.getSiteBySiteId(jetpackSite.getSiteId()));
        assertNull(mSiteStore.getSiteBySiteId(selfHostedSite.getSiteId()));
    }

    @Test
    public void testDeleteSite() throws DuplicateSiteException {
        SiteModel wpComSite = generateWPComSite();

        // Should not cause any errors
        SiteSqlUtils.deleteSite(wpComSite);

        SiteSqlUtils.insertOrUpdateSite(wpComSite);
        int affectedRows = SiteSqlUtils.deleteSite(wpComSite);

        assertEquals(1, affectedRows);
        assertEquals(0, mSiteStore.getSitesCount());
    }

    @Test
    public void testGetWPComSites() throws DuplicateSiteException {
        SiteModel wpComSite = generateWPComSite();
        SiteModel jetpackSiteOverXMLRPC = generateJetpackSiteOverXMLRPC();
        SiteModel jetpackSiteOverRestOnly = generateJetpackSiteOverRestOnly();

        SiteSqlUtils.insertOrUpdateSite(wpComSite);
        SiteSqlUtils.insertOrUpdateSite(jetpackSiteOverXMLRPC);
        SiteSqlUtils.insertOrUpdateSite(jetpackSiteOverRestOnly);

        assertEquals(3, SiteSqlUtils.getWPComAndJetpackSites().getAsCursor().getCount());

        List<SiteModel> wpComSites = SiteSqlUtils.getWPComSites().getAsModel();
        assertEquals(1, wpComSites.size());
        for (SiteModel site : wpComSites) {
            assertNotEquals(jetpackSiteOverXMLRPC.getId(), site.getId());
        }
    }

    @Test
    public void testInsertDuplicateSites() throws DuplicateSiteException {
        SiteModel futureJetpack = generateSelfHostedSiteFutureJetpack();
        SiteModel jetpack = generateJetpackSiteOverRestOnly();

        // Insert a self hosted site that will later be converted to Jetpack
        SiteSqlUtils.insertOrUpdateSite(futureJetpack);

        // Insert the same site but Jetpack powered this time
        SiteSqlUtils.insertOrUpdateSite(jetpack);

        // Previous site should be converted to a Jetpack site and we should see only one site
        int sitesCount = WellSql.select(SiteModel.class).getAsCursor().getCount();
        assertEquals(1, sitesCount);

        List<SiteModel> wpComSites  = SiteSqlUtils.getWPComSites().getAsModel();
        assertEquals(0, wpComSites.size());
        assertEquals(1, SiteSqlUtils.getWPComAndJetpackSites().getAsCursor().getCount());
        List<SiteModel> jetpackSites =
                SiteSqlUtils.getSitesWith(SiteModelTable.IS_JETPACK_CONNECTED, true).getAsModel();
        assertEquals(jetpack.getSiteId(), jetpackSites.get(0).getSiteId());
        assertTrue(jetpackSites.get(0).isJetpackConnected());
        assertFalse(jetpackSites.get(0).isWPCom());
    }

    @Test
    public void testInsertDuplicateSitesError() throws DuplicateSiteException {
        SiteModel futureJetpack = generateSelfHostedSiteFutureJetpack();
        SiteModel jetpack = generateJetpackSiteOverRestOnly();

        // Insert a Jetpack powered site
        SiteSqlUtils.insertOrUpdateSite(jetpack);
        boolean duplicate = false;
        try {
            // Insert the same site but via self hosted this time (this should fail)
            SiteSqlUtils.insertOrUpdateSite(futureJetpack);
        } catch (DuplicateSiteException e) {
            // Caught !
            duplicate = true;
        }
        assertTrue(duplicate);
        int sitesCount = WellSql.select(SiteModel.class).getAsCursor().getCount();
        assertEquals(1, sitesCount);
    }

    @Test
    public void testInsertDuplicateSitesDifferentSchemesError1() throws DuplicateSiteException {
        SiteModel futureJetpack = generateSelfHostedSiteFutureJetpack();
        SiteModel jetpack = generateJetpackSiteOverRestOnly();

        futureJetpack.setXmlRpcUrl("https://pony.com/xmlrpc.php");
        jetpack.setXmlRpcUrl("http://pony.com/xmlrpc.php");

        // Insert a Jetpack powered site
        SiteSqlUtils.insertOrUpdateSite(jetpack);
        boolean duplicate = false;
        try {
            // Insert the same site but via self hosted this time (this should fail)
            SiteSqlUtils.insertOrUpdateSite(futureJetpack);
        } catch (DuplicateSiteException e) {
            // Caught !
            duplicate = true;
        }
        assertTrue(duplicate);
        int sitesCount = WellSql.select(SiteModel.class).getAsCursor().getCount();
        assertEquals(1, sitesCount);
    }

    @Test
    public void testInsertDuplicateSitesDifferentSchemesError2() throws DuplicateSiteException {
        SiteModel futureJetpack = generateSelfHostedSiteFutureJetpack();
        SiteModel jetpack = generateJetpackSiteOverRestOnly();

        futureJetpack.setXmlRpcUrl("http://pony.com/xmlrpc.php");
        jetpack.setXmlRpcUrl("https://pony.com/xmlrpc.php");

        // Insert a Jetpack powered site
        SiteSqlUtils.insertOrUpdateSite(jetpack);
        boolean duplicate = false;
        try {
            // Insert the same site but via self hosted this time (this should fail)
            SiteSqlUtils.insertOrUpdateSite(futureJetpack);
        } catch (DuplicateSiteException e) {
            // Caught !
            duplicate = true;
        }
        assertTrue(duplicate);
        int sitesCount = WellSql.select(SiteModel.class).getAsCursor().getCount();
        assertEquals(1, sitesCount);
    }


    @Test
    public void testGetPostFormats() throws DuplicateSiteException {
        SiteModel site = generateWPComSite();
        SiteSqlUtils.insertOrUpdateSite(site);

        // Set 3 post formats
        SiteSqlUtils.insertOrReplacePostFormats(site, generatePostFormats("Video", "Image", "Standard"));
        List<PostFormatModel> postFormats = mSiteStore.getPostFormats(site);
        assertEquals(3, postFormats.size());

        // Set 1 post format
        SiteSqlUtils.insertOrReplacePostFormats(site, generatePostFormats("Standard"));
        postFormats = mSiteStore.getPostFormats(site);
        assertEquals("Standard", postFormats.get(0).getDisplayName());
    }

    @Test
    public void testSearchSitesByNameMatching() throws DuplicateSiteException {
        SiteModel wpComSite1 = generateWPComSite();
        wpComSite1.setName("Doctor Emmet Brown Homepage");
        SiteModel wpComSite2 = generateWPComSite();
        wpComSite2.setName("Shield Eyes from light");
        wpComSite2.setSiteId(557);
        SiteModel wpComSite3 = generateWPComSite();
        wpComSite3.setName("I remember when this was all farmland as far as the eye could see");
        wpComSite2.setSiteId(558);

        SiteSqlUtils.insertOrUpdateSite(wpComSite1);
        SiteSqlUtils.insertOrUpdateSite(wpComSite2);
        SiteSqlUtils.insertOrUpdateSite(wpComSite3);

        List<SiteModel> matchingSites = SiteSqlUtils.getSitesByNameOrUrlMatching("eye");
        assertEquals(2, matchingSites.size());

        matchingSites = SiteSqlUtils.getSitesByNameOrUrlMatching("EYE");
        assertEquals(2, matchingSites.size());
    }

    @Test
    public void testSearchSitesByNameOrUrlMatching() throws DuplicateSiteException {
        SiteModel wpComSite1 = generateWPComSite();
        wpComSite1.setName("Doctor Emmet Brown Homepage");
        SiteModel wpComSite2 = generateWPComSite();
        wpComSite2.setUrl("shieldeyesfromlight.wordpress.com");
        SiteModel selfHostedSite = generateSelfHostedNonJPSite();
        selfHostedSite.setName("I remember when this was all farmland as far as the eye could see.");

        SiteSqlUtils.insertOrUpdateSite(wpComSite1);
        SiteSqlUtils.insertOrUpdateSite(wpComSite2);
        SiteSqlUtils.insertOrUpdateSite(selfHostedSite);

        List<SiteModel> matchingSites = SiteSqlUtils.getSitesByNameOrUrlMatching("eye");
        assertEquals(2, matchingSites.size());

        matchingSites = SiteSqlUtils.getSitesByNameOrUrlMatching("EYE");
        assertEquals(2, matchingSites.size());
    }

    @Test
    public void testSearchWPComSitesByNameOrUrlMatching() throws DuplicateSiteException {
        SiteModel wpComSite1 = generateWPComSite();
        wpComSite1.setName("Doctor Emmet Brown Homepage");
        SiteModel wpComSite2 = generateWPComSite();
        wpComSite2.setUrl("shieldeyesfromlight.wordpress.com");
        SiteModel selfHostedSite = generateSelfHostedNonJPSite();
        selfHostedSite.setName("I remember when this was all farmland as far as the eye could see.");

        SiteSqlUtils.insertOrUpdateSite(wpComSite1);
        SiteSqlUtils.insertOrUpdateSite(wpComSite2);
        SiteSqlUtils.insertOrUpdateSite(selfHostedSite);

        List<SiteModel> matchingSites = SiteSqlUtils.getWPComAndJetpackSitesByNameOrUrlMatching("eye");
        assertEquals(1, matchingSites.size());

        matchingSites = SiteSqlUtils.getWPComAndJetpackSitesByNameOrUrlMatching("EYE");
        assertEquals(1, matchingSites.size());
    }

    @Test
    public void testRemoveAllSites() throws DuplicateSiteException {
        SiteModel wpComSite = generateWPComSite();
        SiteModel jetpackXMLRPCSite = generateJetpackSiteOverXMLRPC();
        SiteModel jetpackRestSite = generateJetpackSiteOverRestOnly();
        SiteModel selfHostedSite = generateSelfHostedNonJPSite();

        SiteSqlUtils.insertOrUpdateSite(wpComSite);
        SiteSqlUtils.insertOrUpdateSite(jetpackXMLRPCSite);
        SiteSqlUtils.insertOrUpdateSite(jetpackRestSite);
        SiteSqlUtils.insertOrUpdateSite(selfHostedSite);

        // first make sure sites are inserted successfully
        assertEquals(4, mSiteStore.getSitesCount());

        SiteSqlUtils.deleteAllSites();

        assertEquals(0, mSiteStore.getSitesCount());
    }

    @Test
    public void testWPComAutomatedTransfer() throws DuplicateSiteException {
        SiteModel wpComSite = generateWPComSite();
        SiteSqlUtils.insertOrUpdateSite(wpComSite);

        // Turn WP.com site into an Automated Transfer (Jetpack) site
        SiteModel automatedTransferSite = generateWPComSite();
        automatedTransferSite.setIsJetpackInstalled(true);
        automatedTransferSite.setIsJetpackConnected(true);
        automatedTransferSite.setIsWPCom(false);
        automatedTransferSite.setIsAutomatedTransfer(true);

        SiteSqlUtils.insertOrUpdateSite(automatedTransferSite);

        assertEquals(1, mSiteStore.getSitesCount());
        assertEquals(0, mSiteStore.getWPComSitesCount());
        assertEquals(1, mSiteStore.getJetpackSitesCount());
    }

    @Test
    public void testBatchInsertSiteDuplicateWPCom()
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        List<SiteModel> siteList = new ArrayList<>();
        siteList.add(generateTestSite(1, "https://pony1.com", "https://pony1.com/xmlrpc.php", true, true));
        siteList.add(generateTestSite(2, "https://pony2.com", "https://pony2.com/xmlrpc.php", true, true));
        siteList.add(generateTestSite(3, "https://pony3.com", "https://pony3.com/xmlrpc.php", true, true));
        // duplicate with a different id, we should ignore it
        siteList.add(generateTestSite(4, "https://pony3.com", "https://pony3.com/xmlrpc.php", true, true));
        siteList.add(generateTestSite(5, "https://pony4.com", "https://pony4.com/xmlrpc.php", true, true));
        siteList.add(generateTestSite(6, "https://pony5.com", "https://pony5.com/xmlrpc.php", true, true));

        SitesModel sites = new SitesModel(siteList);

        // Use reflection to call a private Store method: equivalent to mSiteStore.updateSites(sites)
        Method createOrUpdateSites = SiteStore.class.getDeclaredMethod("createOrUpdateSites", SitesModel.class);
        createOrUpdateSites.setAccessible(true);
        UpdateSitesResult res = (UpdateSitesResult) createOrUpdateSites.invoke(mSiteStore, sites);

        assertTrue(res.duplicateSiteFound);
        assertEquals(5, res.rowsAffected);
        assertEquals(5, mSiteStore.getSitesCount());
    }

    @Test
    public void testBatchInsertSiteNoDuplicateWPCom()
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        List<SiteModel> siteList = new ArrayList<>();
        siteList.add(generateTestSite(1, "https://pony1.com", "https://pony1.com/xmlrpc.php", true, true));
        siteList.add(generateTestSite(2, "https://pony2.com", "https://pony2.com/xmlrpc.php", true, true));
        siteList.add(generateTestSite(3, "https://pony3.com", "https://pony3.com/xmlrpc.php", true, true));
        siteList.add(generateTestSite(4, "https://pony4.com", "https://pony4.com/xmlrpc.php", true, true));
        siteList.add(generateTestSite(5, "https://pony5.com", "https://pony5.com/xmlrpc.php", true, true));

        SitesModel sites = new SitesModel(siteList);

        // Use reflection to call a private Store method: equivalent to mSiteStore.updateSites(sites)
        Method createOrUpdateSites = SiteStore.class.getDeclaredMethod("createOrUpdateSites", SitesModel.class);
        createOrUpdateSites.setAccessible(true);
        UpdateSitesResult res = (UpdateSitesResult) createOrUpdateSites.invoke(mSiteStore, sites);

        assertFalse(res.duplicateSiteFound);
        assertEquals(5, res.rowsAffected);
        assertEquals(5, mSiteStore.getSitesCount());
    }

    @Test
    public void testSingleInsertSiteDuplicateWPCom()
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        List<SiteModel> siteList = new ArrayList<>();
        siteList.add(generateTestSite(1, "https://pony1.com", "https://pony1.com/xmlrpc.php", true, true));
        SitesModel sites = new SitesModel(siteList);

        // Insert 1 site
        Method createOrUpdateSites = SiteStore.class.getDeclaredMethod("createOrUpdateSites", SitesModel.class);
        createOrUpdateSites.setAccessible(true);
        UpdateSitesResult res = (UpdateSitesResult) createOrUpdateSites.invoke(mSiteStore, sites);

        assertFalse(res.duplicateSiteFound);
        assertEquals(1, res.rowsAffected);
        assertEquals(1, mSiteStore.getSitesCount());

        // Insert same site with different id (considered a duplicate)
        List<SiteModel> siteList2 = new ArrayList<>();
        siteList2.add(generateTestSite(2, "https://pony1.com", "https://pony1.com/xmlrpc.php", true, true));
        SitesModel sites2 = new SitesModel(siteList2);
        createOrUpdateSites.setAccessible(true);
        UpdateSitesResult res2 = (UpdateSitesResult) createOrUpdateSites.invoke(mSiteStore, sites2);

        assertTrue(res2.duplicateSiteFound);
        assertEquals(0, res2.rowsAffected);
        assertEquals(1, mSiteStore.getSitesCount());
    }

    @Test
    public void testUpdateSiteUniqueConstraintFail() throws DuplicateSiteException {
        // Create 2 test sites
        SiteModel site1 = generateTestSite(0, "https://pony1.com", "https://pony1.com/xmlrpc.php", false, true);
        SiteSqlUtils.insertOrUpdateSite(site1);
        SiteModel site2 = generateTestSite(0, "https://pony2.com", "https://pony2.com/xmlrpc.php", false, true);
        SiteSqlUtils.insertOrUpdateSite(site2);

        // Update the second site and reuse the site url and id from the first
        site2.setUrl("https://pony1.com");
        boolean duplicate = false;
        try {
            SiteSqlUtils.insertOrUpdateSite(site2);
        } catch (SQLiteException e) {
            // We should catch a DuplicateSiteException here, but since Roboelectric uses a different SQL stack, we
            // can't catch `SQLiteConstraintException` in SiteSqlUtils.insertOrUpdateSite.
            duplicate = true;
        }
        assertTrue(duplicate);
    }

    @Test
    public void testJetpackSelfHostedAndForceXMLRPC() {
        SiteModel jetpackSite = generateJetpackSiteOverXMLRPC();
        jetpackSite.setOrigin(SiteModel.ORIGIN_WPCOM_REST);
        assertTrue(jetpackSite.isUsingWpComRestApi());

        // Force the origin, it should now use XMLRPC instead of REST.
        jetpackSite.setOrigin(SiteModel.ORIGIN_XMLRPC);
        assertFalse(jetpackSite.isUsingWpComRestApi());
    }

    @Test
    public void testDefaultUsageWpComRestApi() {
        SiteModel wpComSite = generateWPComSite();
        assertTrue(wpComSite.isUsingWpComRestApi());

        SiteModel jetpack1 = generateJetpackSiteOverRestOnly();
        assertTrue(jetpack1.isUsingWpComRestApi());

        SiteModel jetpack2 = generateJetpackSiteOverXMLRPC();
        assertFalse(jetpack2.isUsingWpComRestApi());

        SiteModel pureSelfHosted1 = generateSelfHostedNonJPSite();
        assertFalse(pureSelfHosted1.isUsingWpComRestApi());

        SiteModel pureSelfHosted2 = generateSelfHostedSiteFutureJetpack();
        assertFalse(pureSelfHosted2.isUsingWpComRestApi());
    }
}
