package oneapi.client.impl;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;

import oneapi.client.SMSMessagingClient;
import oneapi.client.impl.OneAPIBaseClientImpl;
import oneapi.config.Configuration;
import oneapi.listener.DeliveryReportListener;
import oneapi.listener.DeliveryStatusNotificationsListener;
import oneapi.listener.InboundMessageListener;
import oneapi.listener.InboundMessageNotificationsListener;
import oneapi.model.*;
import oneapi.model.common.DeliveryInfoList;
import oneapi.model.common.DeliveryReceiptSubscription;
import oneapi.model.common.DeliveryReport;
import oneapi.model.common.DeliveryReportSubscription;
import oneapi.model.common.InboundSMSMessageList;
import oneapi.model.common.MoSubscription;
import oneapi.model.common.ResourceReference;
import oneapi.pushserver.PushServerSimulator;
import oneapi.retriever.DeliveryReportRetriever;
import oneapi.retriever.InboundMessageRetriever;


public class SMSMessagingClientImpl extends OneAPIBaseClientImpl implements SMSMessagingClient {
	private static final String SMS_MESSAGING_OUTBOUND_URL_BASE = "/smsmessaging/outbound";
	private static final String SMS_MESSAGING_INBOUND_URL_BASE = "/smsmessaging/inbound";
	
	private DeliveryReportRetriever deliveryReportRetriever = null;
    private InboundMessageRetriever inboundMessageRetriever = null;
    private volatile List<DeliveryReportListener> deliveryReportPullListenerList = null;
    private volatile List<InboundMessageListener> inboundMessagePullListenerList = null;

    private volatile List<DeliveryStatusNotificationsListener> deliveryStatusNotificationPushListenerList = null;
    private volatile List<InboundMessageNotificationsListener> inboundMessagePushListenerList = null;
    private PushServerSimulator dlrStatusPushServerSimulator;
    private PushServerSimulator inboundMessagesPushServerSimulator;
    
    //*************************SMSMessagingClientImpl Initialization******************************************************************************************************************************************************
    public SMSMessagingClientImpl(Configuration configuration) {
        super(configuration);
    }

    //*************************SMSMessagingClientImpl public******************************************************************************************************************************************************
    /**
     * Send an SMS over OneAPI to one or more mobile terminals using the customized 'SMS' object
     * @param sms (mandatory) object containing data needed to be filled in order to send the SMS
     * @return String Request Id
     */
    @Override
    public String sendSMS(SMSRequest sms){
        StringBuilder urlBuilder = new StringBuilder(SMS_MESSAGING_OUTBOUND_URL_BASE).append("/");
        urlBuilder.append(encodeURLParam(sms.getSenderAddress()));
        urlBuilder.append("/requests");

        HttpURLConnection connection = executePost(appendMessagingBaseUrl(urlBuilder.toString()), sms);
        ResourceReference resourceReference = deserialize(connection, ResourceReference.class, RESPONSE_CODE_201_CREATED, "resourceReference");       
        return GetIdFromResourceUrl(resourceReference.getResourceURL()); 
    }

    /**
     * Query the delivery status over OneAPI for an SMS sent to one or more mobile terminals
     * @param senderAddress (mandatory) is the address from which SMS messages are being sent. Do not URL encode this value prior to passing to this function
     * @param requestId (mandatory) contains the requestId returned from a previous call to the sendSMS function
     * @return DeliveryInfoList
     */
    @Override
    public DeliveryInfoList queryDeliveryStatus(String senderAddress, String requestId) {
        StringBuilder urlBuilder = new StringBuilder(SMS_MESSAGING_OUTBOUND_URL_BASE).append("/");
        urlBuilder.append(encodeURLParam(senderAddress));
        urlBuilder.append("/requests/");
        urlBuilder.append(encodeURLParam(requestId));
        urlBuilder.append("/deliveryInfos");

        HttpURLConnection connection = executeGet(appendMessagingBaseUrl(urlBuilder.toString()));
        return deserialize(connection, DeliveryInfoList.class, RESPONSE_CODE_200_OK, "deliveryInfoList");
    }

    /**
     * Convert JSON to Delivery Info Notification </summary>
     * @param json
     * @return DeliveryInfoNotification
     */
    public DeliveryInfoNotification convertJsonToDeliveryInfoNotification(String json)
    {    
        return convertJSONToObject(json.getBytes(), DeliveryInfoNotification.class, "deliveryInfoNotification");
    }
    
    /**
     * Start subscribing to delivery status notifications over OneAPI for all your sent SMS
     * @param subscribeToDeliveryNotificationsRequest (mandatory) contains delivery notifications subscription data
     * @return String Subscription Id
     */
    @Override
    public String subscribeToDeliveryStatusNotifications(SubscribeToDeliveryNotificationsRequest subscribeToDeliveryNotificationsRequest) {
        StringBuilder urlBuilder = new StringBuilder(SMS_MESSAGING_OUTBOUND_URL_BASE).append("/");
        
        if(null != subscribeToDeliveryNotificationsRequest.getSenderAddress()) {
            urlBuilder.append(encodeURLParam(subscribeToDeliveryNotificationsRequest.getSenderAddress())).append("/");
        }
        urlBuilder.append("subscriptions");

        HttpURLConnection connection = executePost(appendMessagingBaseUrl(urlBuilder.toString()), subscribeToDeliveryNotificationsRequest);
        DeliveryReceiptSubscription deliveryReceiptSubscription = deserialize(connection, DeliveryReceiptSubscription.class, RESPONSE_CODE_201_CREATED, "deliveryReceiptSubscription");      
        return GetIdFromResourceUrl(deliveryReceiptSubscription.getResourceURL()); 
    }

    /**
     * Get delivery notifications subscriptions by sender address
     * @param senderAddress
     * @return DeliveryReportSubscription[]
     */
    @Override
    public DeliveryReportSubscription[] getDeliveryNotificationsSubscriptionsBySender(String senderAddress) {
        StringBuilder urlBuilder = new StringBuilder(SMS_MESSAGING_OUTBOUND_URL_BASE).append("/");
        urlBuilder.append(encodeURLParam(senderAddress));
        urlBuilder.append("/subscriptions");

        HttpURLConnection connection = executeGet(appendMessagingBaseUrl(urlBuilder.toString()));
        return deserialize(connection, DeliveryReportSubscription[].class, RESPONSE_CODE_200_OK, "deliveryReceiptSubscriptions");
    }

    /**
     * Get delivery notifications subscriptions by subscription id
     * @param subscriptionId
     * @return DeliveryReportSubscription
     */
    @Override
    public DeliveryReportSubscription getDeliveryNotificationsSubscriptionById(String subscriptionId) {
        StringBuilder urlBuilder = new StringBuilder(SMS_MESSAGING_OUTBOUND_URL_BASE).append("/subscriptions/");
        urlBuilder.append(encodeURLParam(subscriptionId));

        HttpURLConnection connection = executeGet(appendMessagingBaseUrl(urlBuilder.toString()));
        return deserialize(connection, DeliveryReportSubscription.class, RESPONSE_CODE_200_OK, "deliveryReceiptSubscription");
    }

    /**
     * Get delivery notifications subscriptions by for the current user
     * @return DeliveryReportSubscription[]
     */
    @Override
    public DeliveryReportSubscription[] getDeliveryNotificationsSubscriptions() {
        HttpURLConnection connection = executeGet(appendMessagingBaseUrl(SMS_MESSAGING_OUTBOUND_URL_BASE.concat("/subscriptions")));
        return deserialize(connection, DeliveryReportSubscription[].class, RESPONSE_CODE_200_OK, "deliveryReceiptSubscriptions");
    }

    /**
     * Stop subscribing to delivery status notifications over OneAPI for all your sent SMS
     * @param subscriptionId (mandatory) contains the subscriptionId of a previously created SMS delivery receipt subscription
     */
    @Override
    public void removeDeliveryNotificationsSubscription(String subscriptionId) {
        StringBuilder urlBuilder = new StringBuilder(SMS_MESSAGING_OUTBOUND_URL_BASE).append("/subscriptions/");
        urlBuilder.append(encodeURLParam(subscriptionId));

        HttpURLConnection connection = executeDelete(appendMessagingBaseUrl(urlBuilder.toString()));
        validateResponse(connection, getResponseCode(connection), RESPONSE_CODE_204_NO_CONTENT);
    }

    /**
     * Get SMS messages sent to your Web application over OneAPI using default 'maxBatchSize' = 100
     * @return InboundSMSMessageList
     */
    @Override
    public InboundSMSMessageList getInboundMessages(){
        return this.getInboundMessages(100);
    }

    /**
     * Get SMS messages sent to your Web application over OneAPI
     * @param  maxBatchSize (optional) is the maximum number of messages to get in this request
     * @return InboundSMSMessageList
     */
    @Override
    public InboundSMSMessageList getInboundMessages(int maxBatchSize) {
        //Registration ID is obsolete so any string can be put: e.g. INBOUND
        StringBuilder urlBuilder = new StringBuilder(SMS_MESSAGING_INBOUND_URL_BASE).append("/registrations/INBOUND/messages");
        urlBuilder.append("?maxBatchSize=");
        urlBuilder.append(encodeURLParam(String.valueOf(maxBatchSize)));
       
        HttpURLConnection connection = executeGet(appendMessagingBaseUrl(urlBuilder.toString()));
        return deserialize(connection, InboundSMSMessageList.class, RESPONSE_CODE_200_OK, "inboundSMSMessageList");
    }
    
    /**
     * Convert JSON to Inbound SMS Message Notification
     * @param json
     * @return InboundSMSMessageList
     */
    public InboundSMSMessageList convertJsonToInboundSMSMessageNotificationExample(String json)
    {
        return convertJSONToObject(json.getBytes(), InboundSMSMessageList.class);
    }


    /**
     * Start subscribing to notifications of SMS messages sent to your application over OneAPI
     * @param subscribeToInboundMessagesRequest (mandatory) contains inbound messages subscription data
     * @return String Subscription Id
     */
    @Override
    public String subscribeToInboundMessagesNotifications(SubscribeToInboundMessagesRequest subscribeToInboundMessagesRequest) {
        HttpURLConnection connection = executePost(appendMessagingBaseUrl(SMS_MESSAGING_INBOUND_URL_BASE.concat("/subscriptions")), subscribeToInboundMessagesRequest);
        ResourceReference resourceReference =  deserialize(connection, ResourceReference.class, RESPONSE_CODE_201_CREATED, "resourceReference");
        return GetIdFromResourceUrl(resourceReference.getResourceURL()); 
    }
    
    /**
     * Get inbound messages notifications subscriptions for the current user
     * @return MoSubscription[]
     */
    @Override
    public MoSubscription[] getInboundMessagesSubscriptions(int page, int pageSize) {
    	StringBuilder urlBuilder = new StringBuilder(SMS_MESSAGING_INBOUND_URL_BASE).append("/subscriptions");
    	urlBuilder.append("?page="); 
    	urlBuilder.append(encodeURLParam(String.valueOf(page)));
    	urlBuilder.append("&pageSize="); 
    	urlBuilder.append(encodeURLParam(String.valueOf(pageSize)));

    	HttpURLConnection connection = executeGet(appendMessagingBaseUrl(SMS_MESSAGING_INBOUND_URL_BASE.concat("/subscriptions")));
    	return deserialize(connection, MoSubscription[].class, RESPONSE_CODE_200_OK, "subscriptions");
    }
    
    /**
     * Get inbound messages notifications subscriptions for the current user
     * @return MoSubscription[]
     */
    @Override
    public MoSubscription[] getInboundMessagesSubscriptions() {
    	return getInboundMessagesSubscriptions(1, 10);
    }

    /**
     * Stop subscribing to message receipt notifications for all your received SMS over OneAPI
     * @param subscriptionId (mandatory) contains the subscriptionId of a previously created SMS message receipt subscription
     */
    @Override
    public void removeInboundMessagesSubscription(String subscriptionId) {
        StringBuilder urlBuilder = new StringBuilder(SMS_MESSAGING_INBOUND_URL_BASE).append("/subscriptions/");
        urlBuilder.append(encodeURLParam(subscriptionId));

        HttpURLConnection connection = executeDelete(appendMessagingBaseUrl(urlBuilder.toString()));
        validateResponse(connection, getResponseCode(connection), RESPONSE_CODE_204_NO_CONTENT);
    }

  
    /**
     * Get delivery reports
     * @param limit
     * @return DeliveryReport[]
     */
    @Override
    public DeliveryReport[] getDeliveryReports(int limit) {
    	StringBuilder urlBuilder = new StringBuilder(SMS_MESSAGING_OUTBOUND_URL_BASE).append("/requests/deliveryReports");	
    	urlBuilder.append("?limit=");
    	urlBuilder.append(encodeURLParam(String.valueOf(limit)));
    	
        HttpURLConnection connection = executeGet(appendMessagingBaseUrl(urlBuilder.toString()));
        return deserialize(connection, DeliveryReport[].class, RESPONSE_CODE_200_OK, "deliveryReportList");
    }
    
    /**
     * Get delivery reports
     */
    @Override
    public DeliveryReport[] getDeliveryReports() {
    	return getDeliveryReports(0);
    }

    /**
     * Get delivery reports by Request Id
     * @param requestId
     * @param limit
     * @return DeliveryReport[]
     */
    @Override
    public DeliveryReport[] getDeliveryReportsByRequestId(String requestId, int limit) {
        StringBuilder urlBuilder = new StringBuilder(SMS_MESSAGING_OUTBOUND_URL_BASE).append("/requests/");
        urlBuilder.append(encodeURLParam(requestId));
        urlBuilder.append("/deliveryReports");      
    	urlBuilder.append("?limit=");
    	urlBuilder.append(encodeURLParam(String.valueOf(limit)));
    	
        HttpURLConnection connection = executeGet(appendMessagingBaseUrl(urlBuilder.toString()));
        return deserialize(connection, DeliveryReport[].class, RESPONSE_CODE_200_OK, "deliveryReportList");
    }
    
    /**
     * Get delivery reports by Request Id
     * @param requestId
     * @return DeliveryReport[]
     */
    @Override
    public DeliveryReport[] getDeliveryReportsByRequestId(String requestId) {
    	return getDeliveryReportsByRequestId(requestId, 0);
    }

    /**
     * Add OneAPI PULL 'Delivery Reports' listener
     * @param listener - (new DeliveryReportListener)
     */
    @Override
    public void addPullDeliveryReportListener(DeliveryReportListener listener) {
        if (listener == null) {
            return;
        }

        if (deliveryReportPullListenerList == null) {
        	deliveryReportPullListenerList = new ArrayList<DeliveryReportListener>();
        }
        
        this.deliveryReportPullListenerList.add(listener);
        this.startDeliveryReportRetriever();
    }

    /**
     * Add OneAPI PULL 'INBOUND Messages' listener
     * Messages are pulled automatically depending on the 'inboundMessagesRetrievingInterval' client configuration parameter
     * @param listener - (new InboundMessageListener)
     */
    @Override
    public void addPullInboundMessageListener(InboundMessageListener listener) {
        if (listener == null) {
            return;
        }
        
        if (inboundMessagePullListenerList == null) {
        	inboundMessagePullListenerList = new ArrayList<InboundMessageListener>();
        }

        this.inboundMessagePullListenerList.add(listener);
        this.startInboundMessageRetriever();
    }

    /**
     * Returns INBOUND Message PULL Listeners list
     */
    @Override
    public List<InboundMessageListener> getInboundMessagePullListeners() {
        return inboundMessagePullListenerList;
    }

    /**
     * Returns Delivery Reports PULL Listeners list
     */
    @Override
    public List<DeliveryReportListener> getDeliveryReportPullListeners() {
        return deliveryReportPullListenerList;
    }

    /**
     * Remove PULL Delivery Reports listeners and stop retriever
     */
    @Override
    public void removePullDeliveryReportListeners() {
        stopDeliveryReportRetriever();
        deliveryReportPullListenerList = null; 
        if (LOGGER.isInfoEnabled())
        {
            LOGGER.info("PULL Delivery Report Listeners are successfully released.");
        }
    }
    
    /**
     * Remove PULL INBOUND Messages listeners and stop retriever
     */
    @Override
    public void removePullInboundMessageListeners() {
        stopInboundMessagesRetriever();
        inboundMessagePullListenerList = null;      
        if (LOGGER.isInfoEnabled())
        {
            LOGGER.info("PULL Inbound Message Listeners are successfully released.");
        }
    }
    
    /**
     * Add OneAPI PUSH 'Delivery Status' Notifications listener  and start push server simulator
     */
    public void addPushDeliveryStatusNotificationListener(DeliveryStatusNotificationsListener listener)
    {
        if (listener == null)
        {
            return;
        }

        if (deliveryStatusNotificationPushListenerList == null)
        {
            deliveryStatusNotificationPushListenerList = new ArrayList<DeliveryStatusNotificationsListener>();
        }

        deliveryStatusNotificationPushListenerList.add(listener);

        StartDlrStatusPushServerSimulator();

        if (LOGGER.isInfoEnabled())
        {
            LOGGER.info("Listener is successfully added, push server is started and is waiting for Delivery Status Notifications");
        }
    }

    /**
     * Add OneAPI PUSH 'INBOUND Messages' Notifications listener and start push server simulator
     * @param listener
     */
    public void addPushInboundMessageListener(InboundMessageNotificationsListener listener)
    {
        if (listener == null)
        {
            return;
        }

        if (inboundMessagePushListenerList == null)
        {
            inboundMessagePushListenerList = new ArrayList<InboundMessageNotificationsListener>();
        }

        inboundMessagePushListenerList.add(listener);

        startInboundMessagesPushServerSimulator();

        if (LOGGER.isInfoEnabled())
        {
            LOGGER.info("Listener is successfully added, push server is started and is waiting for Inbound Messages Notifications");
        }
    }
    
    /**
     * Returns Delivery Status Notifications PUSH Listeners list 
     * @return List<DeliveryStatusNotificationsListener>
     */
    public List<DeliveryStatusNotificationsListener> getDeliveryStatusNotificationPushListeners()
    {
    	return deliveryStatusNotificationPushListenerList;   
    }

    /**
     * Returns INBOUND Message Notifications PUSH Listeners list
     * @return List<InboundMessageNotificationsListener>
     */
    public List<InboundMessageNotificationsListener> getInboundMessagePushListeners()
    {
    	return inboundMessagePushListenerList;    
    }

    /**
     *  Remove PUSH Delivery Reports Notifications listeners and stop server
     */
    public void removePushDeliveryStatusNotificationListeners()
    {
        stopDlrStatusPushServerSimulator(); 
        deliveryStatusNotificationPushListenerList = null;
       
        if (LOGGER.isInfoEnabled())
        {
            LOGGER.info("Delivery Status Notification Listeners are successfully removed.");
        }
    }
    
    /**
     *  Remove PUSH Delivery Reports Notifications listeners and stop server
     */
    public void removePushInboundMessageListeners()
    {
        stopInboundMessagesPushServerSimulator();
        inboundMessagePushListenerList = null;

        if (LOGGER.isInfoEnabled())
        {
            LOGGER.info("Inbound Message Listeners are successfully removed.");
        }
    }
    
    //*************************SMSMessagingClientImpl private******************************************************************************************************************************************************
    /**
     * START DLR Retriever
     */
    private void startDeliveryReportRetriever() {
        if (this.deliveryReportRetriever != null) {
            return;
        }

        this.deliveryReportRetriever = new DeliveryReportRetriever();
        int intervalMs = getConfiguration().getDlrRetrievingInterval();
        this.deliveryReportRetriever.start(intervalMs, this);
    }

    /**
     * STOP DLR Retriever and dispose listeners
     */
    private void stopDeliveryReportRetriever() {
        if (deliveryReportRetriever == null) {
            return;
        }

        deliveryReportRetriever.stop();
        deliveryReportRetriever = null;
    }

    /**
     * START INBOUND Messages Retriever
     */
    private void startInboundMessageRetriever() {
        if (this.inboundMessageRetriever != null) {
            return;
        }

        this.inboundMessageRetriever = new InboundMessageRetriever();
        int intervalMs = getConfiguration().getInboundMessagesRetrievingInterval();
        this.inboundMessageRetriever.start(intervalMs, this);
    }

    /**
     * STOP INBOUND Messages Retriever and dispose listeners
     */
    private void stopInboundMessagesRetriever() {
        if (inboundMessageRetriever == null) {
            return;
        }

        inboundMessageRetriever.stop();
        inboundMessageRetriever = null;
    }
    
    private void StartDlrStatusPushServerSimulator()
    {
        if (dlrStatusPushServerSimulator == null)
        {
            dlrStatusPushServerSimulator = new PushServerSimulator(this, getConfiguration().getDlrStatusPushServerSimulatorPort());
            dlrStatusPushServerSimulator.start();
        } 
    }

    private void stopDlrStatusPushServerSimulator()
    {
        if (dlrStatusPushServerSimulator != null)
        {               
            dlrStatusPushServerSimulator.stop(); 
        }
    }

    private void startInboundMessagesPushServerSimulator()
    {
        if (inboundMessagesPushServerSimulator == null)
        {
            inboundMessagesPushServerSimulator = new PushServerSimulator(this, getConfiguration().getInboundMessagesPushServerSimulatorPort());
            inboundMessagesPushServerSimulator.start();
        } 
    }

    private void stopInboundMessagesPushServerSimulator()
    {
        if (inboundMessagesPushServerSimulator != null)
        {
            inboundMessagesPushServerSimulator.stop();   
        }
    }
}