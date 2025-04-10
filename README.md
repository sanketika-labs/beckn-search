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
  "request": {
    "search": {
      "text": "restaurant",
      "geoSpatial": {
        "radius": 5000,
        "location": {
          "lat": 12.9716,
          "lng": 77.5946
        }
      },
      "filters": {
        "cuisine": ["indian", "chinese"],
        "price_range": "medium"
      },
      "page": {
        "from": 0,
        "size": 10
      }
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
│   │   └── org/beckn/
│   │       ├── config/        # Configuration classes
│   │       ├── controller/    # REST controllers
│   │       ├── model/         # Data models
│   │       ├── repository/    # Elasticsearch repositories
│   │       └── service/       # Business logic
│   └── resources/
│       └── application.yml    # Application configuration
└── test/                      # Test classes
```

### Adding New Features

1. Create new model classes in `src/main/java/org/beckn/model/`
2. Add repository interfaces in `src/main/java/org/beckn/repository/`
3. Implement service classes in `src/main/java/org/beckn/service/`
4. Create controller endpoints in `src/main/java/org/beckn/controller/`
5. Add corresponding test classes in `src/test/java/org/beckn/`

## Troubleshooting

1. **Elasticsearch Connection Issues**
   - Verify Elasticsearch is running
   - Check the connection URL in `application.yml`
   - Ensure no firewall is blocking the connection

2. **Build Issues**
   - Clean the project: `./gradlew clean`
   - Refresh dependencies: `./gradlew --refresh-dependencies`

3. **Application Startup Issues**
   - Check the logs in `logs/application.log`
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
