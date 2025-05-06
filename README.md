# Beckn Search Service

A Spring Boot application that provides search functionality using Elasticsearch, supporting text search, geo-spatial search, and filters.

## Prerequisites

- Java 17 or higher
- Gradle 8.0 or higher
- Elasticsearch 8.0 or higher
- Docker (optional, for running Elasticsearch)

## Setup

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd beckn-search
   ```

2. **Set up Elasticsearch**
   
   Option 1: Using Docker (recommended)
   ```bash
   docker run -d --name elasticsearch -p 9200:9200 -p 9300:9300 -e "discovery.type=single-node" -e "xpack.security.enabled=false" elasticsearch:8.0.0
   ```

   Option 2: Install Elasticsearch locally
   - Download Elasticsearch from [elastic.co](https://www.elastic.co/downloads/elasticsearch)
   - Follow the installation instructions for your operating system
   - Start Elasticsearch service

3. **Configure the application**
   
   The application is configured to connect to Elasticsearch at `localhost:9200` by default. If you need to change this, modify the following properties in `src/main/resources/application.yml`:
   ```yaml
   spring:
     elasticsearch:
       uris: http://localhost:9200
   ```

## Building the Application

1. **Build the project**
   ```bash
   ./gradlew build
   ```

2. **Run the tests**
   ```bash
   ./gradlew test
   ```

## Running the Application

1. **Start the application**
   ```bash
   ./gradlew bootRun
   ```

   The application will start on port 8080 by default.

2. **Verify the application is running**
   ```bash
   curl http://localhost:8080/actuator/health
   ```

## API Documentation

The application exposes the following endpoints:

### Search API

- **Endpoint**: `POST /search`
- **Request Body**: JSON payload following the Beckn search request format
- **Response**: Search results with pagination

Example request:
```json
{
   "context": {
      "domain": "deg:ev",
      "transaction_id": "12345678-aaaa-bbbb-cccc-1234567890ab",
      "message_id": "abcdef12-3456-7890-abcd-ef1234567890",
      "timestamp": "2025-04-15T10:30:00Z"
   },
   "message": {
      "intent": {
         "providers": [
            {
               "fulfillments": [
                  {
                     "type": "onsite"
                  }
               ]
            }
         ]
      }
   }
}
```

## Development

### Project Structure

```
src/
├── main/
│   ├── java/
│   │   └── org/beckn/search/
│   │       ├── api/          # REST API controllers
│   │       ├── elasticsearch/# Elasticsearch integration
│   │       ├── model/        # Data models and DTOs
│   │       ├── transformer/  # Response transformers
│   │       └── validation/   # Request validators
│   └── resources/
│       ├── application.properties    # Application configuration
│       └── mappings/                 # Elasticsearch mappings
└── test/
    ├── java/
    │   └── org/beckn/search/
    │       ├── api/          # Controller tests
    │       ├── elasticsearch/# Elasticsearch integration tests
    │       ├── model/        # Model tests
    │       ├── transformer/  # Transformer tests
    │       └── validation/   # Validator tests
    └── resources/
        └── mappings/         # Test mapping files
```

### Adding New Features

1. Create new model classes in `src/main/java/org/beckn/search/model/`
   - Add DTOs for request/response handling
   - Use appropriate annotations for validation and JSON serialization

2. Add Elasticsearch integration in `src/main/java/org/beckn/search/elasticsearch/`
   - Implement search query builders
   - Add index mappings in `src/main/resources/mappings/`

3. Implement transformers in `src/main/java/org/beckn/search/transformer/`
   - Add response transformation logic
   - Handle data format conversions

4. Add validation logic in `src/main/java/org/beckn/search/validation/`
   - Implement request validators
   - Add custom validation rules

5. Create API endpoints in `src/main/java/org/beckn/search/api/`
   - Implement REST controllers
   - Add error handling

6. Add corresponding test classes in `src/test/java/org/beckn/search/`
   - Unit tests for models and transformers
   - Integration tests for Elasticsearch
   - API tests for controllers

## Troubleshooting

1. **Elasticsearch Connection Issues**
   - Verify Elasticsearch is running
   - Check the connection URL in `application.properties`
   - Ensure no firewall is blocking the connection

2. **Build Issues**
   - Clean the project: `./gradlew clean`
   - Refresh dependencies: `./gradlew --refresh-dependencies`

3. **Application Startup Issues**
   - Check the logs in `logs/beckn-search.log`
   - Verify all required services are running
   - Ensure all configuration properties are correct

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.
