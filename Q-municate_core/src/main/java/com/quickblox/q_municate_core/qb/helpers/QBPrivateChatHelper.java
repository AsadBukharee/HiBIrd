package com.quickblox.q_municate_core.qb.helpers;

import android.content.Context;
import android.os.Bundle;

import com.quickblox.chat.model.QBChatMessage;
import com.quickblox.chat.model.QBChatDialog;
import com.quickblox.chat.model.QBDialogType;
import com.quickblox.content.QBContent;
import com.quickblox.content.model.QBFile;
import com.quickblox.core.exception.QBResponseException;
import com.quickblox.q_municate_core.R;
import com.quickblox.q_municate_core.models.NotificationType;
import com.quickblox.q_municate_core.service.QBServiceConsts;
import com.quickblox.q_municate_core.utils.ChatNotificationUtils;
import com.quickblox.q_municate_core.utils.ChatUtils;
import com.quickblox.q_municate_core.utils.DbUtils;
import com.quickblox.q_municate_db.managers.DataManager;
import com.quickblox.q_municate_db.models.Dialog;
import com.quickblox.q_municate_db.models.DialogNotification;
import com.quickblox.q_municate_db.models.DialogOccupant;
import com.quickblox.q_municate_db.models.Message;
import com.quickblox.q_municate_db.models.State;
import com.quickblox.q_municate_db.models.User;
import com.quickblox.users.model.QBUser;

import java.io.File;
import java.util.ArrayList;

public class QBPrivateChatHelper extends QBBaseChatHelper {

    private static final String TAG = QBPrivateChatHelper.class.getSimpleName();

    public QBPrivateChatHelper(Context context) {
        super(context);
        QBNotificationChatListener notificationChatListener = new PrivateChatNotificationListener();
        addNotificationChatListener(notificationChatListener);
    }

    public void init(QBUser user) {
        super.init(user);
    }

    @Override
    public synchronized QBChatDialog createChatLocally(QBChatDialog dialog, Bundle additional) throws QBResponseException {
        currentDialog = dialog;
        int opponentId = additional.getInt(QBServiceConsts.EXTRA_OPPONENT_ID);
        return createPrivateChatIfNotExist(opponentId);
    }

    @Override
    public synchronized void closeChat(QBChatDialog qbDialog, Bundle additional) {
        if (currentDialog != null && currentDialog.getDialogId().equals(qbDialog.getDialogId())) {
            currentDialog = null;
        }
    }

    public void onPrivateMessageReceived(String chat, QBChatMessage qbChatMessage) {
        String dialogId = (String) qbChatMessage.getProperty(ChatNotificationUtils.PROPERTY_DIALOG_ID);
        if (qbChatMessage.getId() != null && dialogId != null) {
            User user = dataManager.getUserDataManager().get(qbChatMessage.getSenderId());
            Dialog dialog = dataManager.getDialogDataManager().getByDialogId(dialogId);
            if (dialog == null) {
                QBChatDialog qbDialog = ChatNotificationUtils.parseDialogFromQBMessage(context, qbChatMessage, QBDialogType.PRIVATE);
                ChatUtils.addOccupantsToQBDialog(qbDialog, qbChatMessage);
                DbUtils.saveDialogToCache(dataManager, qbDialog);
            }
            DbUtils.saveMessageOrNotificationToCache(context, dataManager, dialogId, qbChatMessage, State.DELIVERED, true);
            DbUtils.updateDialogModifiedDate(dataManager, dialogId, ChatUtils.getMessageDateSent(qbChatMessage), true);

            checkForSendingNotification(false, qbChatMessage, user, true);
        }
    }

    public QBFile loadAttachFile(File inputFile) throws Exception {
        QBFile file = null;

        try {
            file = QBContent.uploadFileTask(inputFile, true, null).perform();
        } catch (QBResponseException exc) {
            throw new Exception(context.getString(R.string.dlg_fail_upload_attach));
        }

        return file;
    }

    private void friendRequestMessageReceived(QBChatMessage qbChatMessage, DialogNotification.Type notificationType) {
        String dialogId = (String) qbChatMessage.getProperty(ChatNotificationUtils.PROPERTY_DIALOG_ID);
        Message message = parseReceivedMessage(qbChatMessage);

        if (!dataManager.getUserDataManager().exists(qbChatMessage.getSenderId())) {
            QBRestHelper.loadAndSaveUser(qbChatMessage.getSenderId());
        }

        DialogNotification dialogNotification = ChatUtils.convertMessageToDialogNotification(message);
        dialogNotification.setType(notificationType);

        Dialog dialog = dataManager.getDialogDataManager().getByDialogId(dialogId);
        if (dialog == null) {
            QBChatDialog qbDialog = ChatNotificationUtils.parseDialogFromQBMessage(context, qbChatMessage, QBDialogType.PRIVATE);
            ArrayList<Integer> occupantsIdsList = ChatUtils.createOccupantsIdsFromPrivateMessage(chatCreator.getId(), qbChatMessage.getSenderId());
            qbDialog.setOccupantsIds(occupantsIdsList);
            DbUtils.saveDialogToCache(dataManager, qbDialog);
        }

        DialogOccupant dialogOccupant = dataManager.getDialogOccupantDataManager().getDialogOccupant(dialogId, qbChatMessage.getSenderId());
        DbUtils.saveDialogNotificationToCache(context, dataManager, dialogOccupant, qbChatMessage, true);

        checkForSendingNotification(false, qbChatMessage, dialogOccupant.getUser(), true);
    }

    private class PrivateChatNotificationListener implements QBNotificationChatListener {

        @Override
        public void onReceivedNotification(String notificationTypeString, QBChatMessage chatMessage) {
            NotificationType notificationType = NotificationType.parseByValue(
                    Integer.parseInt(notificationTypeString));
            switch (notificationType) {
                case FRIENDS_REQUEST:
                    friendRequestMessageReceived(chatMessage, DialogNotification.Type.FRIENDS_REQUEST);
                    break;
                case FRIENDS_ACCEPT:
                    friendRequestMessageReceived(chatMessage, DialogNotification.Type.FRIENDS_ACCEPT);
                    break;
                case FRIENDS_REJECT:
                    friendRequestMessageReceived(chatMessage, DialogNotification.Type.FRIENDS_REJECT);
                    break;
                case FRIENDS_REMOVE:
                    friendRequestMessageReceived(chatMessage, DialogNotification.Type.FRIENDS_REMOVE);
                    clearFriendOrUserRequestLocal(chatMessage.getSenderId());
                    break;
            }
        }

        private void clearFriendOrUserRequestLocal(int userId) {
            boolean friend = dataManager.getFriendDataManager().getByUserId(userId) != null;
            boolean outgoingUserRequest = dataManager.getUserRequestDataManager().existsByUserId(userId);
            if (friend) {
                dataManager.getFriendDataManager().deleteByUserId(userId);
            } else if (outgoingUserRequest) {
                dataManager.getUserRequestDataManager().deleteByUserId(userId);
            }
        }
    }
}