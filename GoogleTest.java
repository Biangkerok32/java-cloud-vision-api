package com.vachea.product.moderation.google.test;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.DataStoreCredentialRefreshListener;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.client.util.store.DataStoreFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.vision.v1.Vision;
import com.google.api.services.vision.v1.VisionScopes;
import com.google.api.services.vision.v1.model.AnnotateImageRequest;
import com.google.api.services.vision.v1.model.AnnotateImageResponse;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesResponse;
import com.google.api.services.vision.v1.model.EntityAnnotation;
import com.google.api.services.vision.v1.model.Feature;
import com.google.api.services.vision.v1.model.Image;
import com.google.common.collect.ImmutableList;

/**
 * 
 * Vision:
 * https://developers.google.com/api-client-library/java/apis/vision/v1
 * https://cloud.google.com/vision/docs/reference/rest/v1/images/annotate#Feature
 * https://codelabs.developers.google.com/codelabs/cloud-vision-intro/index.html
 * 
 * Type:
 * TYPE_UNSPECIFIED 	Unspecified feature type.
 * FACE_DETECTION 		Run face detection.
 * LANDMARK_DETECTION 	Run landmark detection.
 * LOGO_DETECTION 		Run logo detection.
 * LABEL_DETECTION 		Run label detection.
 * TEXT_DETECTION 		Run OCR.
 * SAFE_SEARCH_DETECTION 	precedence when both DOCUMENT_TEXT_DETECTION and TEXT_DETECTION are present. Run computer vision models to compute image safe-search properties.
 * IMAGE_PROPERTIES 	Compute a set of image properties, such as the image's dominant colors.
 * 
 */
public class GoogleTest {
	//TODO change your home
	private static final String TMP_REPOSITORY = "/home/vachea/tmp/";
	//TODO remove your token.properties file if you want to ask a new token to Google with new rigts/ACL
	private static final String TMP_TOKEN_PROPERTIES = "/home/vachea/tmp/token.properties";

	private static final String TABLE_ID = "table_id";
    	private static final String DATASET_ID = "dataset_id";
    	private static final String CLIENT_ID = "client_id";
    	private static final String CLIENT_SECRET = "client_secret";
    	private static final String ACCESS_TOKEN = "access_token";
    	private static final String REFRESH_TOKEN = "refresh_token";
	private static final String USER_EMAIL = "toto@gmail.com";

	// Your Google Developer Project number
	private static final String PROJECT_NUMBER = "project_number";

	private static final int MAX_LABELS = 10;

	// Load Client ID/secret from client_secrets.json file
	private static final String CLIENTSECRETS_LOCATION = "client_secrets.json";
	static GoogleClientSecrets clientSecrets = loadClientSecrets();

	static GoogleClientSecrets loadClientSecrets() {
		try {
			GoogleClientSecrets clientSecrets =
					GoogleClientSecrets.load(new JacksonFactory(),
							new InputStreamReader(GoogleTest.class.getResourceAsStream(CLIENTSECRETS_LOCATION)));
			return clientSecrets;
		} catch (Exception e) {
			System.out.println("Could not load client_secrets.json");
			e.printStackTrace();
			return null;
		}
	}
	// For installed applications, use the redirect URI "urn:ietf:wg:oauth:2.0:oob"
	private static final String REDIRECT_URI = "urn:ietf:wg:oauth:2.0:oob";

	// Objects for handling HTTP transport and JSON formatting of API calls
	private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
	private static final JsonFactory JSON_FACTORY = new JacksonFactory();


	public GoogleTest() {
	}

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		//Test Cloud Vision API
		Credential credential = getInteractiveAndStoreGoogleCredential();
		cloudVisionTest(credential);
	}

	public static void cloudVisionTest(final Credential credential) throws IOException {
		Vision vision = new Vision.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
				.setHttpRequestInitializer(new HttpRequestInitializer() {
					public void initialize(HttpRequest httpRequest) throws IOException {
						credential.initialize(httpRequest);
						httpRequest.setConnectTimeout(3 * 60000);  // 3 minutes connect timeout
						httpRequest.setReadTimeout(3 * 60000);  // 3 minutes read timeout
					}
				})
				.setApplicationName("CloudVisionTest")
				.build();

		// The path to the image file to annotate
		String fileName = "images/demo-image.jpg";

		// Reads the image file into memory
		Path path = Paths.get(fileName);
		byte[] data = Files.readAllBytes(path);

		System.out.println("- LABEL DETECTION -");

		AnnotateImageRequest request = new AnnotateImageRequest()
				.setImage(new Image().encodeContent(data))
				.setFeatures(ImmutableList.of(
						new Feature()
						.setType("LABEL_DETECTION")
						.setMaxResults(MAX_LABELS)));

		Vision.Images.Annotate annotate =
				vision.images()
				.annotate(new BatchAnnotateImagesRequest().setRequests(ImmutableList.of(request)));
		// Due to a bug: requests to Vision API containing large images fail when GZipped.
		annotate.setDisableGZipContent(true);

		BatchAnnotateImagesResponse batchResponse = annotate.execute();
		assert batchResponse.getResponses().size() == 1;
		AnnotateImageResponse response = batchResponse.getResponses().get(0);
		if (response.getLabelAnnotations() == null) {
			throw new IOException(
					response.getError() != null
					? response.getError().getMessage()
							: "Unknown error getting image annotations");
		}

		for(EntityAnnotation annotation : response.getLabelAnnotations()) {
			System.out.println(annotation.toString());
		}

		System.out.println("- SAFE SEARCH DETECTION -");

		AnnotateImageRequest requestSafeSearch = new AnnotateImageRequest()
				.setImage(new Image().encodeContent(data))
				.setFeatures(ImmutableList.of(
						new Feature()
						.setType("SAFE_SEARCH_DETECTION")
						.setMaxResults(MAX_LABELS)));

		Vision.Images.Annotate annotateSafeSearch =
				vision.images()
				.annotate(new BatchAnnotateImagesRequest().setRequests(ImmutableList.of(requestSafeSearch)));
		// Due to a bug: requests to Vision API containing large images fail when GZipped.
		annotateSafeSearch.setDisableGZipContent(true);

		BatchAnnotateImagesResponse batchResponseSafeSearch = annotateSafeSearch.execute();
		assert batchResponseSafeSearch.getResponses().size() == 1;
		AnnotateImageResponse responseSafeSearch = batchResponseSafeSearch.getResponses().get(0);
		if (responseSafeSearch.getSafeSearchAnnotation() == null) {
			throw new IOException(
					responseSafeSearch.getError() != null
					? responseSafeSearch.getError().getMessage()
							: "Unknown error getting image annotations");
		}

		System.out.println(responseSafeSearch.getSafeSearchAnnotation().toString());

		
		System.out.println("- TEXT DETECTION -");

		AnnotateImageRequest requestText = new AnnotateImageRequest()
				.setImage(new Image().encodeContent(data))
				.setFeatures(ImmutableList.of(
						new Feature()
						.setType("TEXT_DETECTION")
						.setMaxResults(MAX_LABELS)));

		Vision.Images.Annotate annotateText =
				vision.images()
				.annotate(new BatchAnnotateImagesRequest().setRequests(ImmutableList.of(requestText)));
		// Due to a bug: requests to Vision API containing large images fail when GZipped.
		annotateText.setDisableGZipContent(true);

		BatchAnnotateImagesResponse batchResponseText = annotateText.execute();
		assert batchResponseText.getResponses().size() == 1;
		AnnotateImageResponse responseText = batchResponseText.getResponses().get(0);
		if (responseText.getTextAnnotations() != null) {
			for(EntityAnnotation annotation : responseText.getTextAnnotations()) {
				System.out.println(annotation.toString());
			}
		} else {
			System.out.println("L'image ne comporte pas de texte !");
		}

	}

	public static Credential getGoogleCredentialFromRefresh() throws IOException {
		File tokenFile = new File(TMP_REPOSITORY);
		DataStoreFactory credentialStore = new FileDataStoreFactory(tokenFile);
		Credential credential = new GoogleCredential.Builder()
				.setTransport(HTTP_TRANSPORT)
				.setJsonFactory(JSON_FACTORY)
				.setClientSecrets(CLIENT_ID, CLIENT_SECRET)
				.addRefreshListener(new DataStoreCredentialRefreshListener(USER_EMAIL, credentialStore))
				.build()
				.setAccessToken(ACCESS_TOKEN)
				.setRefreshToken(REFRESH_TOKEN);

		return credential;
	}

	public static Credential getInteractiveAndStoreGoogleCredential() throws IOException {
		Credential credential = null;
		// Attempt to Load existing Refresh Token
		String storedRefreshToken = loadRefreshToken();
		// Check to see if the an existing refresh token was loaded.
		// If so, create a credential and call refreshToken() to get a new
		// access token.
		if (storedRefreshToken != null) {
			// Request a new Access token using the refresh token.
			credential = new GoogleCredential.Builder().setTransport(HTTP_TRANSPORT)
					.setJsonFactory(JSON_FACTORY)
					.setClientSecrets(clientSecrets)
					.build()
					.setFromTokenResponse(new TokenResponse().setRefreshToken(storedRefreshToken));
			credential.refreshToken();

			System.out.println("access token (readKey) = "+credential.getAccessToken());
			System.out.println("refresh token (deleteKey) = "+credential.getRefreshToken());
		} else {
			// Exchange the auth code for an access token and refesh token
			credential = getInteractiveGoogleCredential();
			System.out.println("access token= "+credential.getAccessToken());
			// Store the refresh token for future use.
			storeRefreshToken(credential.getRefreshToken());
		}
		return credential;
	}
	/**
	 * Gets a credential by prompting the user to access a URL and copy/paste the token
	 * @return
	 * @throws IOException
	 */
	public static Credential getInteractiveGoogleCredential() throws IOException {
		List<String> acl = new ArrayList<String>();
		acl.add(VisionScopes.CLOUD_PLATFORM);

		// Create a URL to request that the user provide access to the API
		String authorizeUrl = new GoogleAuthorizationCodeRequestUrl(
				clientSecrets,
				REDIRECT_URI,
				acl).build();
		// Prompt the user to visit the authorization URL, and retrieve the provided authorization code
		System.out.println("Paste this URL into a web browser to authorize Vision:\n" + authorizeUrl);
		System.out.println("... and paste the code you received here: ");
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		String authorizationCode = in.readLine();

		// Create a Authorization flow object

		GoogleAuthorizationCodeFlow flow = 
				new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT,
						JSON_FACTORY,
						clientSecrets,
						acl)
				.setAccessType("offline")
				.setApprovalPrompt("force")
				.build();
		// Exchange the access code for a credential authorizing access
		GoogleTokenResponse response = flow.newTokenRequest(authorizationCode)
				.setRedirectUri(REDIRECT_URI).execute();
		Credential credential = flow.createAndStoreCredential(response, null);
		return credential;
	}

	/**
	 *  Helper to store a new refresh token in token.properties file.
	 */
	private static void storeRefreshToken(String refresh_token) {
		Properties properties = new Properties();
		properties.setProperty("refreshtoken", refresh_token);
		System.out.println("refresh token= " + properties.get("refreshtoken"));
		try {
			properties.store(new FileOutputStream(TMP_TOKEN_PROPERTIES), null);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}


	/**
	 *  Helper to load refresh token from the token.properties file.
	 */
	private static String loadRefreshToken(){
		Properties properties = new Properties();
		try {
			properties.load(new FileInputStream(TMP_TOKEN_PROPERTIES));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return (String) properties.get("refreshtoken");
	}

}
