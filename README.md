# 📊 Microservices System - Analysis and Design

Tài liệu này trình bày chi tiết quá trình **phân tích** và **thiết kế** hệ thống quản lý nghỉ phép dựa trên kiến trúc microservices.

---

## 1. 🎯 Problem Statement

**Bài toán:**  
Doanh nghiệp cần một hệ thống cho phép nhân viên gửi yêu cầu nghỉ phép, quản lý phê duyệt hoặc từ chối các yêu cầu này, đồng thời cập nhật chính xác số ngày nghỉ còn lại và lịch sử nghỉ phép của từng nhân viên. Hệ thống phải đảm bảo tự động hóa quy trình, minh bạch, phân quyền rõ ràng và dễ dàng mở rộng.

**Người dùng:**
- **Nhân viên:**  
  - Gửi yêu cầu nghỉ phép (chọn loại nghỉ, thời gian, lý do).
  - Xem lịch sử nghỉ phép, số ngày nghỉ còn lại.
  - Nhận thông báo về trạng thái yêu cầu (được duyệt/từ chối).
- **Quản lý:**  
  - Nhận thông báo khi có yêu cầu nghỉ phép mới từ nhân viên.
  - Xem, phê duyệt hoặc từ chối các yêu cầu nghỉ phép.
  - Theo dõi lịch sử phê duyệt và quản lý nhân viên dưới quyền.

**Mục tiêu:**
- Tự động hóa quy trình xử lý đơn nghỉ phép.
- Đảm bảo phân quyền rõ ràng giữa nhân viên và quản lý.
- Cập nhật chính xác dữ liệu nghỉ phép, lịch sử nghỉ.
- Dễ dàng mở rộng, bảo trì và tích hợp với các hệ thống khác.

**Dữ liệu xử lý:**
- Thông tin nhân viên (họ tên, email, phòng ban, chức vụ, số ngày nghỉ còn lại, người quản lý).
- Đơn nghỉ phép (ngày bắt đầu, ngày kết thúc, loại nghỉ, lý do, trạng thái).
- Lịch sử nghỉ phép (các kỳ nghỉ đã được phê duyệt).
- Thông báo (nộp đơn, được duyệt, bị từ chối).

---

## 2. 🧩 Identified Microservices

| Service Name           | Responsibility                                                                 | Tech Stack    |
|------------------------|-------------------------------------------------------------------------------|---------------|
| **employee-service**       | Quản lý thông tin nhân viên, lịch sử nghỉ, số ngày nghỉ còn lại                | Python Flask  |
| **leave-request-service**  | Tiếp nhận, lưu trữ yêu cầu nghỉ phép, xác minh thông tin                       | Python Flask  |
| **approval-service**       | Xử lý logic phê duyệt/từ chối, cập nhật trạng thái, gửi thông báo              | Python Flask  |
| **notification-service**   | Gửi thông báo đến nhân viên và quản lý khi có sự kiện liên quan đến nghỉ phép  | Python Flask  |
| **manager-service**        | Quản lý thông tin quản lý (manager)                                           | Python Flask  |
| **gateway**                | Định tuyến request đến các microservices, bảo vệ truy cập                      | Nginx         |

**Giải thích chi tiết:**
- **employee-service:** Lưu trữ và quản lý thông tin nhân viên, lịch sử nghỉ phép, số ngày nghỉ còn lại. Cung cấp API để các service khác truy vấn thông tin nhân viên.
- **leave-request-service:** Nhận yêu cầu nghỉ phép từ nhân viên, lưu trữ trạng thái ban đầu là PENDING, không liên kết khóa ngoại trực tiếp với employee-service để đảm bảo tách biệt dữ liệu.
- **approval-service:** Quản lý quá trình phê duyệt/từ chối yêu cầu nghỉ phép, cập nhật trạng thái đơn, gửi thông báo kết quả sang notification-service.
- **notification-service:** Lưu trữ và gửi thông báo đến người dùng (nhân viên hoặc quản lý) khi có sự kiện liên quan đến nghỉ phép.
- **manager-service:** Quản lý thông tin các quản lý, phục vụ cho việc phân quyền và gửi thông báo.
- **gateway:** Đóng vai trò là cổng vào duy nhất, định tuyến request đến các service, bảo vệ hệ thống khỏi truy cập trái phép.

---

## 3. 🔄 Service Communication

**Cách các service giao tiếp:**
- **Gateway ⇄ Các service:**  
  - Giao tiếp qua REST API, sử dụng HTTP.
  - Gateway định tuyến request dựa trên URL đến đúng service.
- **Internal service-to-service:**  
  - Các service gọi trực tiếp REST API của nhau thông qua tên service nội bộ (Docker Compose network).
  - Ví dụ: approval-service gọi employee-service để xác thực thông tin nhân viên, hoặc gọi notification-service để gửi thông báo.
- **notification-service:**  
  - Nhận request từ approval-service hoặc leave-request-service khi có sự kiện cần gửi thông báo.

**Lưu ý:**  
- Không sử dụng message queue ở phiên bản hiện tại, nhưng có thể mở rộng với Redis hoặc RabbitMQ nếu cần xử lý bất đồng bộ hoặc tăng hiệu năng.

---

## 4. 🗂️ Data Design

**employee-service**
- **Employee**
  - id (PK), name, email, department, position, total_leave_days, used_leave_days, manager_id, created_at, updated_at
- **LeaveHistory**
  - id (PK), employee_id, start_date, end_date, leave_type, status, created_at

**leave-request-service**
- **LeaveRequest**
  - id (PK), employee_id, start_date, end_date, leave_type, reason, status (PENDING/APPROVED/REJECTED), created_at, updated_at

**approval-service**
- **Approval**
  - id (PK), request_id (tham chiếu logic đến LeaveRequest), employee_id, manager_id, status, created_at, updated_at

**notification-service**
- **Notification**
  - id (PK), recipient_id (employee_id hoặc manager_id), recipient_role, notification_type, message, created_at

**manager-service**
- **Manager**
  - id (PK), name, email, department

**Đặc điểm thiết kế dữ liệu:**
- Mỗi service có database riêng biệt (PostgreSQL).
- Không dùng foreign key giữa các service để đảm bảo tách biệt, tăng tính độc lập và dễ mở rộng.
- Các trường trạng thái (status) giúp tracking quy trình xử lý đơn nghỉ phép.

**Sơ đồ tổng quan (ERD rút gọn):**
```
> Xem sơ tại docs/assets/ERD-diagram.png
```

---

## 5. 🔐 Security Considerations

- **Phân quyền:**  
  - Mỗi API kiểm tra vai trò (role) của người dùng (EMPLOYEE, MANAGER).
  - Chỉ quản lý mới được phê duyệt/từ chối đơn nghỉ phép.
- **Xác thực:**  
  - Mỗi service xác thực và kiểm tra dữ liệu đầu vào riêng biệt.
  - Không cho phép truy cập trực tiếp vào các service trừ qua Gateway.
- **Bảo vệ dữ liệu:**  
  - Không lộ thông tin nhạy cảm qua API.
  - Kiểm tra dữ liệu đầu vào để tránh injection, lỗi logic.

---

## 6. 📦 Deployment Plan

- Sử dụng `docker-compose` để quản lý toàn bộ hệ thống, đảm bảo các service và database khởi động đồng bộ.
- Mỗi microservice có Dockerfile riêng, dễ dàng build và deploy độc lập.
- Cấu hình môi trường (database URL, secret key, v.v.) lưu trong file `.env`.
- PostgreSQL làm database cho từng service.
- Redis có thể tích hợp thêm cho hàng đợi/thông báo nếu mở rộng.
- Có thể triển khai trên cloud hoặc server vật lý tùy nhu cầu.

---

## 7. 🎨 Architecture Diagram

> Xem sơ đồ kiến trúc tại docs/assets/architecture-diagram.png

*Mỗi service có database riêng biệt (PostgreSQL).*

---

## ✅ Summary

Kiến trúc microservices giúp phân tách rõ ràng chức năng từng thành phần: quản lý nhân viên, xử lý yêu cầu nghỉ, phê duyệt, thông báo.  
Các service độc lập, dễ bảo trì, dễ mở rộng, có thể phát triển song song.  
Giao tiếp qua REST API giúp tích hợp linh hoạt, dễ kiểm thử.  
Triển khai bằng Docker giúp hệ thống đồng nhất, dễ dàng mở rộng và kiểm soát cấu hình.  
Thiết kế dữ liệu tách biệt giúp giảm rủi ro khi thay đổi, nâng cấp từng service.

---

## Author

- B21DCCN001 - Nguyễn Đức An  
- B21DCCN373 - Vũ Văn Hiếu  
- B21DCCN638 - Phùng Ngọc Quý  


