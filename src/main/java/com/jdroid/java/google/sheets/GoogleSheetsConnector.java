package com.jdroid.java.google.sheets;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.services.sheets.v4.Sheets;
import com.jdroid.java.exception.UnexpectedException;
import com.jdroid.java.google.AbstractConnector;

import java.io.IOException;
import java.util.List;

public class GoogleSheetsConnector extends AbstractConnector {
	
	private Sheets sheets;
	
	public GoogleSheetsConnector(String applicationName, List<String> scopes, String userCredentialsDirPath) {
		super(applicationName, scopes, userCredentialsDirPath);
		try {
			sheets = getSheetsService();
		} catch (IOException e) {
			throw new UnexpectedException(e);
		}
	}
	
	/**
	 * Build and return an authorized Sheets API client service.
	 *
	 * @return an authorized Sheets API client service
	 * @throws IOException
	 */
	protected Sheets getSheetsService() throws IOException {
		Credential credential = authorize();
		Sheets.Builder builder = new Sheets.Builder(getHttpTransport(), getJsonFactory(), credential);
		builder.setApplicationName(getApplicationName());
		return builder.build();
	}
	
	public Sheets getSheets() {
		return sheets;
	}
}
