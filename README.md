# DOHIS Backend
Java 17 required.

## Required environment variables

- `MONGODB_URI`
- `JWT_SECRET`
- `GOOGLE_CLIENT_ID`
- `GOOGLE_CLIENT_SECRET`
- `GOOGLE_REDIRECT_URI`
- `GOOGLE_OWNER_REFRESH_TOKEN`
- `GOOGLE_DRIVE_ROOT_FOLDER_ID`
- `GOOGLE_DRIVE_REPORTS_FOLDER_ID`
- `APP_CORS_ALLOWED_ORIGINS`

## Notes

- `application.properties` uses environment placeholders.
- `application-example.properties` shows the expected configuration structure.
- The project currently authenticates Google Drive access using OAuth client credentials plus owner refresh token.

## Local run

1. Set the required environment variables.
2. Make sure Java 17 is installed.
3. Run the project with:

```bash
./mvnw spring-boot:run
```

On Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

You can also run the project from IntelliJ using the same environment variables.

## Environment variable reference

- `MONGODB_URI`: MongoDB connection string including the database name.
- `JWT_SECRET`: secret used to sign and validate JWT tokens.
- `GOOGLE_CLIENT_ID`: OAuth client ID used for Google authentication.
- `GOOGLE_CLIENT_SECRET`: OAuth client secret used for Google authentication.
- `GOOGLE_REDIRECT_URI`: OAuth redirect URI configured in Google Cloud.
- `GOOGLE_OWNER_REFRESH_TOKEN`: refresh token used by the backend to access Google Drive as the owner flow.
- `GOOGLE_DRIVE_ROOT_FOLDER_ID`: root Google Drive folder managed by the application.
- `GOOGLE_DRIVE_REPORTS_FOLDER_ID`: Google Drive folder where activity reports are stored.
- `APP_CORS_ALLOWED_ORIGINS`: comma-separated list of allowed frontend origins.

## Important setup details

- `MONGODB_URI` must include the database name, not only the cluster host.
- `GOOGLE_REDIRECT_URI` must exactly match the redirect URI configured in Google Cloud.
- `APP_CORS_ALLOWED_ORIGINS` must be a comma-separated list.

## Profiles

- Default run: uses the base configuration.
- `dev` profile: enables more verbose Spring Security logging.

To run with the `dev` profile, set:

```text

SPRING_PROFILES_ACTIVE=dev

## Validation

To run the automated tests:

```bash
./mvnw test
```

On Windows PowerShell:

```powershell
.\mvnw.cmd test
```
The test suite uses the `test` profile and its configuration from `src/test/resources/application-test.properties`.
