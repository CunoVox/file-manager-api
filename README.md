# File Manager API

Backend Spring Boot cho hệ thống quản lý file, thư mục, chia sẻ file, thùng rác, tài khoản người dùng, admin dashboard, storage node S3/MinIO, developer API key và billing/PayOS.

## Công nghệ

- Java 11 source compatibility
- Spring Boot 2.7.18
- Spring Security + JWT
- Spring Data JPA
- MySQL 8.4
- AWS SDK S3 compatible storage
- Lombok
- Springdoc OpenAPI
- Maven Wrapper

## Cấu trúc chính

```text
src/main/java/cloud/haovo/filemanager
├── api
│   ├── common       # response lỗi và exception handler dùng chung
│   ├── controller   # REST controllers
│   ├── request      # DTO request cho toàn bộ API
│   └── response     # DTO response cho toàn bộ API
├── config           # cấu hình Spring/CORS/security bean
├── domain           # entity và enum
├── repository       # Spring Data repositories
├── security         # JWT, filter, user principal
└── service          # business logic
```

Quy ước hiện tại:

- Request DTO đặt trong `api/request`.
- Response DTO đặt trong `api/response`.
- Không gom request/response của nhiều API vào class lồng nhau.
- Controller chỉ nhận request DTO và trả response DTO, không trả trực tiếp entity nếu không cần.

## Cấu hình môi trường

Tạo file `.env` từ `.env.example`:

```powershell
Copy-Item .env.example .env
```

Các biến quan trọng:

```env
MYSQL_HOST=localhost
MYSQL_PORT=3306
MYSQL_DATABASE=file_manager
MYSQL_USERNAME=root
MYSQL_PASSWORD=root
PUBLIC_BASE_URL=http://localhost:8085
WEB_BASE_URL=http://localhost:5173
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:5174
APP_ENCRYPTION_KEY=
```

`APP_ENCRYPTION_KEY` nên được cấu hình khi chạy môi trường thật để mã hoá secret như SMTP/PayOS/storage credentials.

## Chạy database

Project có sẵn `docker-compose.yml` để chạy MySQL:

```powershell
docker compose up -d
```

MySQL mặc định:

- Host: `localhost`
- Port: `3306`
- Database: `file_manager`
- User: `root`
- Password: `root`

## Chạy ứng dụng

Trên Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

Trên macOS/Linux:

```bash
./mvnw spring-boot:run
```

API chạy mặc định tại:

```text
http://localhost:8085
```

Swagger UI:

```text
http://localhost:8085/swagger-ui.html
```

## Kiểm tra build

```powershell
.\mvnw.cmd test
```

Nếu máy chưa cài Maven vẫn chạy được vì project đã có Maven Wrapper.

## Endpoint nhóm chính

- `/api/v1/auth` - đăng ký, đăng nhập, refresh token, profile, 2FA, email verification
- `/api/v1/files` - quản lý file
- `/api/v1/folders` - quản lý thư mục
- `/api/v1/trash` - file/thư mục đã xoá mềm
- `/api/v1/shared` - file được chia sẻ
- `/api/v1/notifications` - thông báo
- `/api/v1/developer` - API key, usage, file/folder qua developer API
- `/api/v1/billing` - plan, checkout, order, subscription
- `/api/v1/admin` - user, dashboard, storage node, migration, settings, billing admin
- `/view/{id}` và `/download/{id}` - public file view/download

## Ghi chú khi mở bằng IntelliJ IDEA

- Bật annotation processing cho Lombok nếu IDE chưa tự nhận.
- Project đã pin Lombok `1.18.48` và cấu hình annotation processor trong Maven.
- Nếu IntelliJ dùng JBR/JDK mới, Maven build vẫn chạy ổn qua wrapper.

## Lệnh hữu ích

```powershell
# Build nhanh
.\mvnw.cmd test

# Chạy app
.\mvnw.cmd spring-boot:run

# Dừng database Docker
docker compose down

# Xoá cả volume database local
docker compose down -v
```
