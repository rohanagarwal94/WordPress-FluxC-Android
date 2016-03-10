package org.wordpress.android.stores.persistence;

import com.wellsql.generated.AccountModelTable;
import com.wellsql.generated.AccountSettingsModelTable;
import com.yarolegovich.wellsql.WellSql;

import org.wordpress.android.stores.model.AccountModel;
import org.wordpress.android.stores.model.AccountSettingsModel;

import java.util.List;

public class AccountSqlUtils {
    public static void insertOrUpdateAccount(AccountModel account) {
        List<AccountModel> accountResults = WellSql.selectUnique(AccountModel.class).getAsModel();
        if (accountResults.isEmpty()) {
            // insert
            WellSql.insert(account).execute();
        } else {
            // update
            int oldId = accountResults.get(0).getId();
            WellSql.update(AccountModel.class).whereId(oldId)
                    .put(account, new UpdateAllExceptId<AccountModel>()).execute();
        }
    }

    public static AccountModel getAccountByLocalId(long localId) {
        List<AccountModel> accountResult = WellSql.select(AccountModel.class)
                .where().equals(AccountModelTable.ID, localId)
                .endWhere().getAsModel();
        return accountResult.isEmpty() ? null : accountResult.get(0);
    }

    public static void insertOrUpdateAccountSettings(AccountSettingsModel settings) {
        List<AccountSettingsModel> accountResults =
                WellSql.selectUnique(AccountSettingsModel.class).getAsModel();
        if (accountResults.isEmpty()) {
            // insert
            WellSql.insert(settings).execute();
        } else {
            // update
            int oldId = accountResults.get(0).getId();
            WellSql.update(AccountSettingsModel.class).whereId(oldId)
                    .put(settings, new UpdateAllExceptId<AccountSettingsModel>()).execute();
        }
    }

    public static AccountSettingsModel getAccountSettingsForSite(long siteId) {
        List<AccountSettingsModel> accountResult = WellSql.select(AccountSettingsModel.class)
                .where().equals(AccountSettingsModelTable.PRIMARY_SITE_ID, siteId)
                .endWhere().getAsModel();
        return accountResult.isEmpty() ? null : accountResult.get(0);
    }
}
