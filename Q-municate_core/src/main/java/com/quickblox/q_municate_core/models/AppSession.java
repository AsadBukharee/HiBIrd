package com.quickblox.q_municate_core.models;

import android.text.TextUtils;
import android.util.Log;

import com.quickblox.auth.model.QBProvider;
import com.quickblox.auth.session.BaseService;
import com.quickblox.auth.session.QBSessionManager;
import com.quickblox.auth.session.QBSessionParameters;
import com.quickblox.core.exception.BaseServiceException;
import com.quickblox.auth.QBAuth;
import com.quickblox.q_municate_core.utils.helpers.CoreSharedHelper;
import com.quickblox.users.model.QBUser;

import java.io.Serializable;
import java.util.Date;

public class AppSession implements Serializable {

    private static final Object lock = new Object();
    private static AppSession activeSession;

    private CoreSharedHelper coreSharedHelper;
    private LoginType loginType;
    private QBUser qbUser;

    private AppSession(QBUser qbUser) {
        coreSharedHelper = CoreSharedHelper.getInstance();
        this.qbUser = qbUser;
        this.loginType = getLoginTypeBySessionParameters(QBSessionManager.getInstance().getSessionParameters());
        save();
    }

    public static void startSession(QBUser user) {
        activeSession = new AppSession(user);
    }

    private static AppSession getActiveSession() {
        synchronized (lock) {
            return activeSession;
        }
    }

    public static AppSession load() {

        int userId = CoreSharedHelper.getInstance().getUserId();
        String userFullName = CoreSharedHelper.getInstance().getUserFullName();

        QBUser qbUser = new QBUser();
        qbUser.setId(userId);
        qbUser.setEmail(CoreSharedHelper.getInstance().getUserEmail());
        qbUser.setPassword(CoreSharedHelper.getInstance().getUserPassword());
        qbUser.setFullName(userFullName);
        qbUser.setFacebookId(CoreSharedHelper.getInstance().getFBId());
        qbUser.setTwitterId(CoreSharedHelper.getInstance().getTwitterId());
        qbUser.setTwitterDigitsId(CoreSharedHelper.getInstance().getTwitterDigitsId());


        return new AppSession(qbUser);
    }

    public static boolean isSessionExistOrNotExpired(long expirationTime) {
            QBSessionManager qbSessionManager = QBSessionManager.getInstance();
            String token = qbSessionManager.getToken();
            if (token == null) {
                Log.d("AppSession", "token == null");
                return false;
            }
            Date tokenExpirationDate = qbSessionManager.getTokenExpirationDate();
            long tokenLiveOffset = tokenExpirationDate.getTime() - System.currentTimeMillis();
            return tokenLiveOffset > expirationTime;
    }

    public static AppSession getSession() {
        AppSession activeSession = AppSession.getActiveSession();
        if (activeSession == null) {
            activeSession = AppSession.load();
        }
        return activeSession;
    }

    public void closeAndClear() {
        coreSharedHelper.clearUserData();

        activeSession = null;
    }

    public QBUser getUser() {
        return qbUser;
    }

    public void save() {
        saveUser(qbUser);
    }

    public void updateUser(QBUser qbUser) {
        this.qbUser = qbUser;
        saveUser(this.qbUser);
    }

    private void saveUser(QBUser user) {
        coreSharedHelper.saveUserId(user.getId());
        coreSharedHelper.saveUserEmail(user.getEmail());
        coreSharedHelper.saveUserPassword(user.getPassword());
        coreSharedHelper.saveUserFullName(user.getFullName());
        coreSharedHelper.saveFBId(user.getFacebookId());
        coreSharedHelper.saveTwitterId(user.getTwitterId());
        coreSharedHelper.saveTwitterDigitsId(user.getTwitterDigitsId());
    }

    public boolean isLoggedIn() {
        return qbUser != null && !TextUtils.isEmpty(QBSessionManager.getInstance().getToken());
    }

    public boolean isSessionExist() {
        return !TextUtils.isEmpty(QBSessionManager.getInstance().getToken());
    }

    public LoginType getLoginType() {
        return loginType;
    }

    private LoginType getLoginTypeBySessionParameters(QBSessionParameters sessionParameters){
        LoginType result = null;
        if(sessionParameters == null){
            return null;
        }
        String socialProvider = sessionParameters.getSocialProvider();
        if(socialProvider == null){
            loginType = LoginType.EMAIL;
        } else if (socialProvider.equals(QBProvider.FACEBOOK)){
            loginType = LoginType.FACEBOOK;
        } else if (socialProvider.equals(QBProvider.TWITTER_DIGITS)){
            loginType = LoginType.TWITTER_DIGITS;
        }
        return result;
    }


}