package org.wordpress.android.stores.model;

import com.yarolegovich.wellsql.core.Identifiable;
import com.yarolegovich.wellsql.core.annotation.Column;
import com.yarolegovich.wellsql.core.annotation.PrimaryKey;
import com.yarolegovich.wellsql.core.annotation.Table;

import org.wordpress.android.stores.Payload;

@Table
public class AccountSettingsModel implements Identifiable, Payload {
    @PrimaryKey
    @Column private int mId;
    @Column private long mPrimarySiteId;
    @Column private String mFirstName;
    @Column private String mLastName;
    @Column private String mAboutMe;
    @Column private String mDate;
    @Column private String mNewEmail;
    @Column private boolean mPendingEmailChange;
    @Column private String mWebAddress;

    public AccountSettingsModel() {
        init();
    }

    private void init() {
        mPrimarySiteId = 0;
        mFirstName = "";
        mLastName = "";
        mAboutMe = "";
        mDate = "";
        mNewEmail = "";
        mPendingEmailChange = false;
        mWebAddress = "";
    }

    @Override
    public int getId() {
        return mId;
    }

    @Override
    public void setId(int id) {
        mId = id;
    }

    public void setPrimarySiteId(long primarySiteId) {
        mPrimarySiteId = primarySiteId;
    }

    public long getPrimarySiteId() {
        return mPrimarySiteId;
    }

    public void setFirstName(String firstName) {
        mFirstName = firstName;
    }

    public String getFirstName() {
        return mFirstName;
    }

    public void setLastName(String lastName) {
        mLastName = lastName;
    }

    public String getLastName() {
        return mLastName;
    }

    public void setAboutMe(String aboutMe) {
        mAboutMe = aboutMe;
    }

    public String getAboutMe() {
        return mAboutMe;
    }

    public void setDate(String date) {
        mDate = date;
    }

    public String getDate() {
        return mDate;
    }

    public void setNewEmail(String newEmail) {
        mNewEmail = newEmail;
    }

    public String getNewEmail() {
        return mNewEmail;
    }

    public void setPendingEmailChange(boolean pendingEmailChange) {
        mPendingEmailChange = pendingEmailChange;
    }

    public boolean getPendingEmailChange() {
        return mPendingEmailChange;
    }

    public void setWebAddress(String webAddress) {
        mWebAddress = webAddress;
    }

    public String getWebAddress() {
        return mWebAddress;
    }
}
