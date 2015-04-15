package com.ajmaradiaga.adaptermodules;

import javax.ejb.Stateless;
import java.io.*;
import java.rmi.RemoteException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.ejb.EJBException;
import javax.ejb.SessionBean;
import javax.ejb.SessionContext;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;

import com.sap.aii.af.lib.mp.module.Module;
import com.sap.aii.af.lib.mp.module.ModuleContext;
import com.sap.aii.af.lib.mp.module.ModuleData;
import com.sap.aii.af.lib.mp.module.ModuleException;
import com.sap.engine.interfaces.messaging.api.*;
import com.sap.engine.interfaces.messaging.api.auditlog.AuditAccess;
import com.sap.engine.interfaces.messaging.api.auditlog.AuditLogStatus;
import com.sap.tc.logging.Location;

/**
 * Session Bean implementation class SetConnectModule
 */
@Stateless
public class SetContentTypeModule implements SessionBean, Module,
SetContentTypeModuleRemote, SetContentTypeModuleLocal {

	/**
	 * 
	 */
	private static final long serialVersionUID = -6310735817589189001L;
	SessionContext myContext = null;

	private Location location = null;
	private AuditAccess audit = null;

	String SIGNATURE = "process(ModuleContext moduleContext, ModuleData inputModuleData)";
	MessageKey key = null;

	/**
	 * Default constructor.
	 */
	public SetContentTypeModule() {
	}

	@Override
	public void ejbActivate() throws EJBException, RemoteException {
	}

	@Override
	public void ejbPassivate() throws EJBException, RemoteException {
	}

	@Override
	public void ejbRemove() throws EJBException, RemoteException {
	}

	@Override
	public void setSessionContext(SessionContext arg0) throws EJBException,
	RemoteException {
	}

	@Override
	public ModuleData process(ModuleContext moduleContext,
			ModuleData inputModuleData) throws ModuleException {

		// Create the location always new to avoid serialization/transient of
		// location
		try {
			location = Location.getLocation(this.getClass().getName());
		} catch (Exception t) {
			t.printStackTrace();
			ModuleException me = new ModuleException(
					"Unable to create trace location", t);
			throw me;
		}
		Object obj = null;
		Message msg = null;

		try {
			obj = inputModuleData.getPrincipalData();

			msg = (Message) obj;

			XMLPayload xmlpayload = msg.getDocument();

			key = new MessageKey(msg.getMessageId(), MessageDirection.OUTBOUND);

			audit = PublicAPIAccessFactory.getPublicAPIAccess()
			.getAuditAccess();
			audit.addAuditLogEntry(key, AuditLogStatus.SUCCESS,
					"SetContentTypeModule: Module called");

			/*
			 *****
			 *Print MessagePropertyKeys
			 *****

			Set<MessagePropertyKey> keys = msg.getMessagePropertyKeys();

			for(MessagePropertyKey key : keys){
				printMessage(key.getPropertyName() + " - " + key.getPropertyNamespace(),null);
			}
			 */

			String _contentTypeValue = this.getModuleParameter(moduleContext,
					"ContentType", "plain/text");
			String _timeStampFormatParameter = this.getModuleParameter(
					moduleContext, "TimestampFormat", null);
			String _fileNameMaskValue = this.getModuleParameter(moduleContext,
					"Filename", null);

			if (_fileNameMaskValue != null) {

				String _fileExt = "";

				if(_fileNameMaskValue.lastIndexOf(".") > -1){
					_fileExt = _fileNameMaskValue.substring(_fileNameMaskValue.lastIndexOf(".") + 1);
				}

				printMessage("File Extension: " + _fileExt, null);

				//Set the filename as Dynamic Configuration
				String _dcNS = this.getModuleParameter(moduleContext,
						"DC.Namespace", null);
				String _dcAttrName = this.getModuleParameter(moduleContext,
						"DC.AttributeName", null);
				String _dcFileExt = this.getModuleParameter(moduleContext,
						"DC.SubstituteFileExtension", "ZIP");

				//Handle Timestamp Mask in Filename
				if (_fileNameMaskValue.contains("[Timestamp]")) {
					String _timeStampFormat = "yyyyMMdd-HHmmss-SSS";

					if (_timeStampFormatParameter != null) {
						_timeStampFormat = _timeStampFormatParameter;
					}

					DateFormat dateFormat = new SimpleDateFormat(_timeStampFormat);
					Date date = new Date();

					_fileNameMaskValue = _fileNameMaskValue.replace("[Timestamp]", dateFormat
							.format(date));

					printMessage("Filename -> " + _fileNameMaskValue, null);
				}

				//If DC Attributes are configured in the Module Configuration
				if(_dcNS != null && _dcAttrName != null) {

					String _dcValue = _fileNameMaskValue;

					if(_fileExt != ""){
						_dcValue = _fileNameMaskValue.replace(_fileExt, _dcFileExt);
					}

					msg.setMessageProperty(new MessagePropertyKey(_dcAttrName, _dcNS), _dcValue);

					printMessage("Set Message Property (DC): " + _dcAttrName + " | " + _dcNS + ": " + _fileNameMaskValue.replace(_fileExt, _dcFileExt), null);
				}
			}
			
			//Adds _fileNameMaskValue if Filename parameter was specified
			String _messageContentType = _contentTypeValue + ((_fileNameMaskValue != null) ? "; name="
			+ _fileNameMaskValue : "");

			//Update ContentType of Payload
			xmlpayload.setContentType(_messageContentType);

			printMessage("Set Message ContentType to " + _messageContentType,
					null);

			/* *********************
			 * Read channel contents
			 * *********************
			 * 
			 * Channel ch = (Channel)
			 * LookupManager.getInstance().getCPAObject(CPAObjectType.CHANNEL,
			 * moduleContext.getChannelID());
			 * 
			 * String[] chAttr = ch.getAttributeNames();
			 * 
			 * for(int i = 0; i < chAttr.length; i++) { message = chAttr[i];
			 * location.debugT(SIGNATURE, message, null);
			 * audit.addAuditLogEntry(key, AuditLogStatus.SUCCESS, message,
			 * null); }
			 */

			DocumentBuilderFactory factory;
			factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();

			Document document = builder.parse((InputStream) xmlpayload
					.getInputStream());

			// Transforming the DOM object to Stream object.
			TransformerFactory tfactory = TransformerFactory.newInstance();
			Transformer transformer = tfactory.newTransformer();
			Source src = new DOMSource(document);
			ByteArrayOutputStream myBytes = new ByteArrayOutputStream();
			Result dest = new StreamResult(myBytes);
			transformer.transform(src, dest);
			byte[] docContent = myBytes.toByteArray();
			if (docContent != null) {
				xmlpayload.setContent(docContent);
				inputModuleData.setPrincipalData(msg);
			}

		} catch (Exception e) {
			printMessage(completeStackTrace(e), AuditLogStatus.ERROR);
			ModuleException me = new ModuleException(e);
			throw me;
		}
		return inputModuleData;
	}

	private String getModuleParameter(ModuleContext moduleContext,
			String paramName, String defaultValue) {
		String paramValue = "";
		String message = "";

		paramValue = (String) moduleContext.getContextData(paramName);

		if (paramValue == null) {
			message = paramName + " parameter is not set. Default used: "
			+ defaultValue;
			paramValue = defaultValue;
			printMessage(message, AuditLogStatus.WARNING);
		}

		printMessage(paramName + " is set to " + paramValue, null);

		return paramValue;
	}

	private String completeStackTrace(Exception e) {
		String retVal = "";

		StackTraceElement[] stack = e.getStackTrace();

		for (int i = 0; i < stack.length; i++) {
			retVal += stack[i];
		}

		return retVal;
	}

	private void printMessage(String message, AuditLogStatus status) {

		if (status == null) {
			status = AuditLogStatus.SUCCESS;
		}

		location.debugT(SIGNATURE, message, null);
		audit.addAuditLogEntry(key, status, message, null);
	}
}
