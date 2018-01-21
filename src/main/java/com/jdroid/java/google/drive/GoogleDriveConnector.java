package com.jdroid.java.google.drive;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.InputStreamContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.ParentReference;
import com.google.common.collect.Lists;
import com.jdroid.java.exception.UnexpectedException;
import com.jdroid.java.google.AbstractConnector;
import com.jdroid.java.utils.LoggerUtils;

import org.slf4j.Logger;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class GoogleDriveConnector extends AbstractConnector {
	
	private static final Logger LOGGER = LoggerUtils.getLogger(GoogleDriveConnector.class);

	public static final String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";

	private class CustomProgressListener implements MediaHttpUploaderProgressListener {

		public void progressChanged(MediaHttpUploader uploader) throws IOException {
			switch (uploader.getUploadState()) {
				case INITIATION_STARTED:
					LOGGER.info("File upload initiation has started!");
					break;
				case INITIATION_COMPLETE:
					LOGGER.info("File upload initiation is complete!");
					break;
				case MEDIA_IN_PROGRESS:
					LOGGER.info("File upload progress: " + uploader.getProgress());
					break;
				case MEDIA_COMPLETE:
					LOGGER.info("Upload is complete!");
			}
		}
	}

	private Drive drive;

	public GoogleDriveConnector(String applicationName, List<String> scopes, String userCredentialsDirPath) {
		super(applicationName, scopes, userCredentialsDirPath);
		try {
			drive = getDriveService();
		} catch (IOException e) {
			throw new UnexpectedException(e);
		}
	}

	/**
	 * Build and return an authorized Drive client service.
	 *
	 * @return an authorized Drive client service
	 * @throws IOException
	 */
	protected Drive getDriveService() throws IOException {
		Credential credential = authorize();
		Drive.Builder builder = new Drive.Builder(getHttpTransport(), getJsonFactory(), credential);
		builder.setApplicationName(getApplicationName());
		return builder.build();
	}

	public void createFolders(List<String> folders) throws IOException {
		String parentId = "root";
		for (String folderName : folders) {
			File folder = createFolder(folderName, parentId);
			parentId = folder.getId();
		}
	}

	public File createFolder(String folderName, String parentId) throws IOException {
		File folder = locateFile(folderName, FOLDER_MIME_TYPE, parentId);
		if (folder == null) {
			File metaData = new File();
			metaData.setTitle(folderName);
			metaData.setMimeType(FOLDER_MIME_TYPE);

			if (parentId != null) {
				metaData.setParents(Lists.newArrayList(new ParentReference().setId(parentId)));
			}

			folder = drive.files().insert(metaData).execute();
		}
		return folder;
	}

	public void uploadFile(String mimeType, String sourcePath, List<String> targetPaths) throws IOException {
		File parent = locateParent(targetPaths);
		uploadFile(mimeType, sourcePath, parent);
	}

	public void uploadFile(String mimeType, String sourcePath, File parent) throws IOException {

		java.io.File contentFile = new java.io.File(sourcePath);
		File fileToRemove = locateFile(contentFile.getName(), mimeType, parent != null ? parent.getId() : null);
		if (fileToRemove != null) {
			drive.files().delete(fileToRemove.getId()).execute();
		}

		InputStreamContent mediaContent = new InputStreamContent(mimeType, new BufferedInputStream(new FileInputStream(contentFile)));
		mediaContent.setLength(contentFile.length());

		File metaData = new File();
		metaData.setTitle(contentFile.getName());
		metaData.setMimeType(mimeType);

		if (parent != null) {
			metaData.setParents(Lists.newArrayList(new ParentReference().setId(parent.getId())));
		}

		Drive.Files.Insert request = drive.files().insert(metaData, mediaContent);
		request.getMediaHttpUploader().setProgressListener(new CustomProgressListener());
		request.execute();
	}

	public List<File> listFiles() throws IOException  {
		List<File> result = new ArrayList<File>();
		Drive.Files.List request = drive.files().list();

		do {
			try {
				FileList files = request.execute();
				result.addAll(files.getItems());
				request.setPageToken(files.getNextPageToken());
			} catch (IOException e) {
				LOGGER.error("An error occurred: " + e);
				request.setPageToken(null);
			}
		} while (request.getPageToken() != null &&
				request.getPageToken().length() > 0);

		return result;
	}

	public File locateFile(String title, String mimeType, List<String> targetPaths) throws IOException {
		File parent = locateParent(targetPaths);
		return locateFile(title, mimeType, parent.getId());
	}
	
	public File locateFile(String title, String mimeType, String parentId) throws IOException {
		StringBuilder query = new StringBuilder();
		query.append("(title='");
		query.append(title);
		query.append("') and (mimeType = '");
		query.append(mimeType);
		query.append("') and (not trashed) and ('");
		if (parentId != null) {
			query.append(parentId);
			query.append("' in parents)");
		}

		FileList fileList = drive.files().list().setQ(query.toString()).execute();
		return (fileList != null && fileList.getItems() != null && !fileList.getItems().isEmpty()) ? fileList.getItems().get(0) : null;
	}
	
	public InputStream downloadFile(String title, String mimeType, List<String> targetPaths) throws IOException {
		return downloadFile(locateFile(title, mimeType, targetPaths));
	}
	
	/**
	 * Download a file's content.
	 *
	 * @param file Drive File instance.
	 * @return InputStream containing the file's content if successful, {@code null} otherwise.
	 */
	public InputStream downloadFile(File file) throws IOException {
		if (file != null && file.getDownloadUrl() != null && file.getDownloadUrl().length() > 0) {
			HttpResponse resp = getDriveService().getRequestFactory().buildGetRequest(new GenericUrl(file.getDownloadUrl())).execute();
			return resp.getContent();
		} else {
			// The file doesn't have any content stored on Drive.
			return null;
		}
	}

	public File locateParent(List<String> paths) throws IOException {
		File result = null;
		if (paths != null) {
			for (String path : paths) {
				final StringBuilder query = new StringBuilder();

				query.append("(title='");
				query.append(path);
				query.append("') and (mimeType='application/vnd.google-apps.folder') and (not trashed)");

				if (result != null) {
					query.append(" and ('");
					query.append(result.getId());
					query.append("' in parents)");
				} else {
					query.append(" and ('root' in parents)");
				}

				final FileList fileList = drive.files().list().setQ(query.toString()).execute();

				if ((fileList == null) || (fileList.getItems() == null) || (fileList.getItems().isEmpty())) {
					throw new IllegalArgumentException("Invalid Google Drive path. Forgot to create folders?");
				}
				result = fileList.getItems().get(0);
			}
		}

		if (result == null) {
			throw new IllegalArgumentException("Invalid Google Drive path.");
		}
		return result;
	}
	
	public Drive getDrive() {
		return drive;
	}
}
