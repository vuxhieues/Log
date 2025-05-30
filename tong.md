# 🧩 Hệ thống Phê duyệt yêu cầu nghỉ phép - Microservices

---

B21DCCN001 - Nguyễn Đức An  
B21DCCN373 - Vũ Văn Hiếu  
B21DCCN638 - Phùng Ngọc Quý  

---

Dự án này xây dựng hệ thống quản lý nhân sự theo kiến trúc microservices, gồm các service độc lập giao tiếp qua API Gateway, sử dụng Docker Compose để triển khai đồng bộ.


Mô tả nghiệp vụ:
Quy trình phê duyệt yêu cầu nghỉ phép cho phép nhân viên nộp đơn xin nghỉ phép
qua hệ thống. Quản lý sau đó sẽ xem xét yêu cầu và phê duyệt hoặc từ chối dựa trên tình
trạng làm việc của nhân viên và các quy định nghỉ phép của công ty. Nếu đơn xin nghỉ
được phê duyệt, hệ thống sẽ gửi thông báo cho nhân viên và cập nhật tình trạng nghỉ phép
của họ.
Yêu cầu phân tích thiết kế hướng dịch vụ cho nghiệp vụ (Usecase):
Phân tích và thiết kế hệ thống phê duyệt yêu cầu nghỉ phép dựa trên kiến trúc hướng
dịch vụ (SOA), đảm bảo quy trình từ khi nhân viên nộp đơn, xác minh thông tin, đến phê
duyệt hoặc từ chối, và thông báo kết quả được thực hiện tự động và chính xác.
Mô tả chi tiết các bước nghiệp vụ:
1. Nhân viên nộp yêu cầu nghỉ phép: Nhân viên nhập thông tin về ngày nghỉ dự kiến,
loại nghỉ phép (ví dụ: nghỉ ốm, nghỉ phép năm), và lý do nộp đơn xin nghỉ.
2. Nhận thông tin chi tiết về nhân viên: Hệ thống nhận thông tin về nhân viên, bao
gồm mã nhân viên, tên, và phòng ban.
3. Kiểm tra lịch sử nghỉ phép: Hệ thống truy xuất lịch sử nghỉ phép của nhân viên để
kiểm tra số ngày nghỉ phép đã sử dụng trong năm.
4. Kiểm tra số ngày nghỉ còn lại: Hệ thống xác minh xem nhân viên còn bao nhiêu
ngày nghỉ phép và liệu yêu cầu có vượt quá số ngày nghỉ hiện có hay không.
5. Gửi yêu cầu đến quản lý: Nếu thông tin hợp lệ, hệ thống gửi yêu cầu nghỉ phép
đến quản lý trực tiếp của nhân viên để phê duyệt.
6. Quản lý nhận thông báo yêu cầu phê duyệt: Quản lý nhận được thông báo về yêu
cầu nghỉ phép của nhân viên và truy cập hệ thống để xem chi tiết.
7. Xem xét yêu cầu: Quản lý kiểm tra thông tin yêu cầu, bao gồm ngày nghỉ, lý do,
và tình trạng công việc hiện tại.
8. Phê duyệt hoặc từ chối yêu cầu: Quản lý quyết định phê duyệt hoặc từ chối yêu
cầu dựa trên thông tin đã xem xét.
9. Nếu phê duyệt, hệ thống gửi thông báo chấp nhận: Nếu quản lý phê duyệt yêu
cầu, hệ thống gửi thông báo chấp nhận nghỉ phép cho nhân viên và cập nhật lịch
nghỉ.
10. Nếu từ chối, hệ thống gửi thông báo từ chối: Nếu quản lý từ chối yêu cầu, hệ
thống gửi thông báo từ chối cho nhân viên kèm lý do.
11. Cập nhật trạng thái nghỉ phép của nhân viên: Nếu yêu cầu được phê duyệt, hệ
thống cập nhật trạng thái nghỉ phép của nhân viên trong hệ thống quản lý nhân sự.
12. Kết thúc quy trình: Quy trình kết thúc sau khi hệ thống gửi thông báo và cập nhật
thông tin.

---

## 🛠️ Tech Stack
- **Front-end:** ReactJS, Material UI, React Router, React Toastify
- **API Gateway:** Nginx (reverse proxy)
- **Back-end:** Python Flask (cho tất cả các service)
- **Database:** PostgreSQL 
- **Containerization:** Docker, Docker Compose
- **API Documentation:** OpenAPI (Swagger YAML)

---
## 📁 Project Structure

```
├── README.md                       # Project instructions
├── .env.example                    # Example environment variables
├── docker-compose.yml              # Multi-container setup for all services
├── docs/                           # Documentation folder
│   ├── architecture.md             # System architecture
│   ├── analysis-and-design.md      # System analysis and design
│   ├── asset/                      # Images, diagrams, etc.
│   └── api-specs/                  # OpenAPI specs for all services
│       ├── approval-service.yaml
│       ├── employee-service.yaml
│       ├── leave-request-service.yaml
│       ├── manager-service.yaml
│       ├── nofitication-service.yaml
│       ├── service-a.yaml
│       └── service-b.yaml
├── front-end/                      # React front-end
│   ├── Dockerfile
│   ├── nginx.conf
│   ├── package.json
│   ├── README.md
│   ├── build/
│   ├── public/
│   └── src/
│       ├── App.js
│       ├── index.js
│       ├── components/
│       ├── config/
│       ├── context/
│       ├── hooks/
│       ├── pages/
│       └── services/
├── gateway/                        # API Gateway (Nginx)
│   ├── Dockerfile
│   └── src/
│       └── nginx.conf
├── scripts/                        # DB init scripts
│   ├── Dockerfile.db-init
│   ├── init-db.py
│   ├── init.sh
│   └── requirements.txt
└── services/                       # Microservices
    ├── approval-service/
    │   ├── Dockerfile
    │   ├── readme.md
    │   ├── requirements.txt
    │   └── src/app.py
    ├── employee-service/
    │   ├── Dockerfile
    │   ├── readme.md
    │   ├── requirements.txt
    │   └── src/app.py
    ├── leave-request-service/
    │   ├── Dockerfile
    │   ├── readme.md
    │   ├── requirements.txt
    │   └── src/app.py
    ├── manager-service/
    │   ├── Dockerfile
    │   ├── readme.md
    │   ├── requirements.txt
    │   └── src/app.py
    ├── notification-service/
    │   ├── Dockerfile
    │   ├── readme.md
    │   ├── requirements.txt
    │   └── src/app.py
    ├── service-a/
    │   └── readme.md
    └── service-b/
        └── readme.md
```

---

## 🚀 Hướng dẫn sử dụng

1. **Clone repository**

   ```bash
   git clone <repo-url>
   cd microservices-assignment-starter
   ```

2. **Tạo file môi trường**

   ```bash
   cp .env.example .env
   ```

3. **Chạy toàn bộ hệ thống**

   ```bash
   docker-compose up --build
   ```

4. **Truy cập front-end tại:**
   ```bash
   http://localhost:4000
   ```

5. **Tài liệu API**
   - Đặc tả OpenAPI cho từng service tại `docs/api-specs/*.yaml`
   - Xem chi tiết ví dụ request/response trong từng file readme.md của mỗi service.

---

## 🧩 Các Service chính

- **manager-service**: Quản lý trưởng phòng (CRUD, không xóa), xác thực manager cho employee-service.
- **employee-service**: Quản lý nhân viên, liên kết manager, kiểm tra manager_id qua manager-service.
- **notification-service**: Quản lý thông báo, phân loại theo vai trò (manager/employee), API lấy/gửi thông báo.
- **leave-request-service**: Quản lý đơn nghỉ phép của nhân viên.
- **approval-service**: Quản lý phê duyệt đơn nghỉ phép.
- **gateway**: API Gateway (Nginx) route các request đến từng service.

---

## ✅ Checklist Hoàn thành
- [x] Tách manager thành service riêng, chuẩn hóa API, tài liệu.
- [x] Chuẩn hóa notification-service, phân loại thông báo, bổ sung API lấy thông báo.
- [x] employee-service kiểm tra manager_id qua manager-service.
- [x] Viết OpenAPI YAML cho từng service.
- [x] Viết lại README.md tổng thể và từng service.
- [x] Đảm bảo chạy toàn bộ hệ thống với 1 lệnh: `docker-compose up`.

---

## 👩‍💻 Thành viên & Đóng góp
- **B21DCCN001 - Nguyễn Đức An**: Front-end cho dự án.
- **B21DCCN373 - Vũ Văn Hiếu**: Back-end cho dự án.
- **B21DCCN638 - Phùng Ngọc Quý**: Tài liệu dự án.


---

## 📚 Tài liệu tham khảo
- [Microservices Patterns](https://microservices.io/patterns/index.html)
- [Docker Compose Docs](https://docs.docker.com/compose/)
- [Flask Documentation](https://flask.palletsprojects.com/)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)

---

## 📬 Liên hệ
- Email: [hungdn@ptit.edu.vn](mailto:hungdn@ptit.edu.vn)
- GitHub template: [hungdn1701/microservices-assignment-starter](https://github.com/hungdn1701/microservices-assignment-starter)

Chúc các bạn thành công! 💪🚀

