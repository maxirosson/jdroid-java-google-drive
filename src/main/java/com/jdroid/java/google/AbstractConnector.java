package com.jdroid.java.google;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.jdroid.java.exception.UnexpectedException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.List;

public abstract class AbstractConnector {
	
	private String applicationName;
	private String userCredentialsDirPath;
	private String userCredentialsFileName = "credentials.json";

	private FileDataStoreFactory dataStoreFactory;
	private JsonFactory jsonFactory;
	private HttpTransport httpTransport;
	private List<String> scopes;
	
	public AbstractConnector(String applicationName, List<String> scopes, String userCredentialsDirPath) {
		try {
			this.applicationName = applicationName;
			jsonFactory = JacksonFactory.getDefaultInstance();
			httpTransport = GoogleNetHttpTransport.newTrustedTransport();
			dataStoreFactory = new FileDataStoreFactory(new java.io.File(userCredentialsDirPath));
			this.scopes = scopes;
			this.userCredentialsDirPath = userCredentialsDirPath;
		} catch (GeneralSecurityException | IOException e) {
			throw new UnexpectedException(e);
		}
	}
	
	/**
	 * Creates an authorized Credential object.
	 *
	 * @return an authorized Credential object.
	 * @throws IOException
	 */
	protected Credential authorize() throws IOException {
		// Load client secrets.
		InputStream in = new FileInputStream(userCredentialsDirPath + "/" + userCredentialsFileName);
		GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(jsonFactory, new InputStreamReader(in));
		
		// Build flow and trigger user authorization request.
		GoogleAuthorizationCodeFlow.Builder builder = new GoogleAuthorizationCodeFlow.Builder(
				httpTransport, jsonFactory, clientSecrets, scopes);
		builder.setDataStoreFactory(dataStoreFactory);
		builder.setAccessType("offline");
		GoogleAuthorizationCodeFlow flow = builder.build();
		return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
	}
	
	public HttpTransport getHttpTransport() {
		return httpTransport;
	}
	
	public JsonFactory getJsonFactory() {
		return jsonFactory;
	}
	
	public String getApplicationName() {
		return applicationName;
	}

	public void setUserCredentialsFileName(String userCredentialsFileName) {
		this.userCredentialsFileName = userCredentialsFileName;
	}
}
