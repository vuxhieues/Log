# System Architecture

## Overview

Hệ thống quản lý nghỉ phép được thiết kế theo kiến trúc microservices nhằm tự động hóa quy trình xử lý đơn nghỉ phép, đảm bảo minh bạch, phân quyền rõ ràng, dễ bảo trì và mở rộng. Mỗi thành phần trong hệ thống đảm nhiệm một chức năng riêng biệt, giao tiếp với nhau qua REST API thông qua API Gateway. Hệ thống hướng tới khả năng mở rộng độc lập từng service, đảm bảo tính sẵn sàng và an toàn dữ liệu.

---

## System Components

### 1. **employee-service**
- **Chức năng:** Quản lý thông tin nhân viên, lịch sử nghỉ phép, số ngày nghỉ còn lại.
- **Nhiệm vụ:**  
  - Lưu trữ và cập nhật thông tin nhân viên (họ tên, email, phòng ban, chức vụ, tổng số ngày nghỉ, số ngày đã nghỉ, người quản lý).
  - Cung cấp API cho các service khác truy vấn thông tin nhân viên.
  - Lưu lịch sử các kỳ nghỉ đã được phê duyệt.

### 2. **leave-request-service**
- **Chức năng:** Tiếp nhận và lưu trữ yêu cầu nghỉ phép từ nhân viên.
- **Nhiệm vụ:**  
  - Nhận yêu cầu nghỉ phép từ nhân viên (qua API Gateway).
  - Lưu trữ yêu cầu với trạng thái khởi tạo là PENDING.
  - Không liên kết khóa ngoại trực tiếp với employee-service để đảm bảo tách biệt dữ liệu.
  - Cung cấp API cho approval-service truy vấn và cập nhật trạng thái yêu cầu.

### 3. **approval-service**
- **Chức năng:** Xử lý logic phê duyệt hoặc từ chối yêu cầu nghỉ phép.
- **Nhiệm vụ:**  
  - Nhận yêu cầu phê duyệt từ leave-request-service hoặc từ quản lý.
  - Xác thực thông tin nhân viên qua employee-service.
  - Cập nhật trạng thái đơn nghỉ phép (APPROVED/REJECTED).
  - Gửi thông báo kết quả sang notification-service.

### 4. **notification-service**
- **Chức năng:** Gửi thông báo đến nhân viên và quản lý khi có sự kiện liên quan đến nghỉ phép.
- **Nhiệm vụ:**  
  - Nhận thông báo từ approval-service hoặc leave-request-service.
  - Lưu trữ lịch sử thông báo.
  - Gửi thông báo đến đúng người nhận (employee hoặc manager) dựa trên sự kiện.

### 5. **manager-service**
- **Chức năng:** Quản lý thông tin các quản lý (manager).
- **Nhiệm vụ:**  
  - Lưu trữ thông tin quản lý (họ tên, email, phòng ban).
  - Hỗ trợ phân quyền và gửi thông báo đến quản lý.

### 6. **API Gateway (Nginx)**
- **Chức năng:** Định tuyến request từ client đến các service, bảo vệ truy cập, quản lý luồng dữ liệu.
- **Nhiệm vụ:**  
  - Là điểm truy cập duy nhất từ bên ngoài vào hệ thống.
  - Định tuyến request dựa trên URL đến đúng service.
  - Có thể tích hợp xác thực, logging, rate limiting, load balancing.

---

## Communication

- **REST API:** Tất cả các service giao tiếp với nhau qua REST API, sử dụng HTTP.
- **API Gateway:** Đóng vai trò trung gian, nhận request từ client và chuyển tiếp đến service phù hợp.
- **Internal Networking:** Các service sử dụng tên service nội bộ (Docker Compose network) để gọi nhau trực tiếp khi cần (ví dụ: approval-service gọi employee-service hoặc notification-service).
- **Notification Flow:** notification-service nhận thông báo từ approval-service hoặc leave-request-service khi có sự kiện (nộp đơn, duyệt đơn, từ chối đơn).

---

## Data Flow

1. **Nhân viên gửi yêu cầu nghỉ phép:**  
   - Gửi request đến API Gateway.
   - Gateway chuyển tiếp đến leave-request-service.
   - leave-request-service lưu yêu cầu với trạng thái PENDING.

2. **Quản lý phê duyệt hoặc từ chối yêu cầu:**  
   - Quản lý nhận thông báo có yêu cầu mới (notification-service).
   - Quản lý gửi quyết định duyệt/từ chối qua API Gateway đến approval-service.
   - approval-service xác thực thông tin nhân viên qua employee-service, cập nhật trạng thái đơn nghỉ phép.
   - approval-service gửi thông báo kết quả sang notification-service.

3. **Nhận thông báo:**  
   - notification-service gửi thông báo đến nhân viên hoặc quản lý dựa trên sự kiện.
   - Lưu lịch sử thông báo để truy xuất sau này.

4. **Truy vấn thông tin:**  
   - Các service có thể truy vấn thông tin nhân viên, quản lý, lịch sử nghỉ phép qua API nội bộ.

5. **Lưu trữ dữ liệu:**  
   - Mỗi service sử dụng một database PostgreSQL riêng biệt, không chia sẻ dữ liệu trực tiếp.
   - Không sử dụng foreign key giữa các service để đảm bảo tách biệt, tăng tính độc lập và dễ mở rộng.

---

## External Dependencies

- **PostgreSQL:** Mỗi service sử dụng một instance database riêng biệt.
- **Docker Compose:** Quản lý toàn bộ hệ thống, đảm bảo các service và database khởi động đồng bộ.
- **Redis (tùy chọn):** Có thể tích hợp thêm cho hàng đợi/thông báo nếu mở rộng.
- **Nginx:** Làm API Gateway, hỗ trợ cân bằng tải, bảo vệ truy cập.

---

## Diagram

### High-level Architecture (PlantUML)

```plantuml
@startuml
actor "Client UI" as Client
node "API Gateway\n(Nginx)" as Gateway
node "employee-service" as Emp
node "leave-request-service" as LeaveReq
node "approval-service" as Approval
node "notification-service" as Notify
node "manager-service" as Manager

database "employee-db" as EmpDB
database "leave-request-db" as LeaveReqDB
database "approval-db" as ApprovalDB
database "notification-db" as NotifyDB
database "manager-db" as ManagerDB

Client --> Gateway
Gateway --> Emp
Gateway --> LeaveReq
Gateway --> Approval
Gateway --> Notify
Gateway --> Manager

Emp --> EmpDB
LeaveReq --> LeaveReqDB
Approval --> ApprovalDB
Notify --> NotifyDB
Manager --> ManagerDB

Approval --> Notify : Gửi thông báo
LeaveReq --> Approval : Gửi yêu cầu phê duyệt
Approval --> Emp : Lấy thông tin nhân viên
@enduml
```

---

## Scalability & Fault Tolerance

### Scalability
- **Horizontal Scaling:** Mỗi service có thể mở rộng độc lập bằng cách tăng số lượng container (scale-out) mà không ảnh hưởng đến các service khác.
- **API Gateway:** Hỗ trợ cân bằng tải, phân phối request đều đến các instance của từng service.
- **Stateless Services:** Các service được thiết kế stateless, dễ dàng mở rộng và thay thế.

### Fault Tolerance
- **Isolation:** Nếu một service gặp sự cố, các service khác vẫn hoạt động bình thường nhờ tách biệt logic và dữ liệu.
- **Database Isolation:** Dữ liệu được lưu trữ riêng biệt cho từng service, giảm thiểu rủi ro khi nâng cấp hoặc bảo trì từng thành phần.
- **Health Check:** Các service cung cấp endpoint health check để kiểm tra trạng thái hoạt động, hỗ trợ tự động khởi động lại khi gặp lỗi.

### Extensibility & Maintainability
- **Extensibility:** Có thể dễ dàng bổ sung thêm service mới hoặc tích hợp với các hệ thống khác mà không ảnh hưởng đến toàn bộ hệ thống.
- **Maintainability:** Mỗi service có thể phát triển, triển khai, bảo trì độc lập, giảm thiểu xung đột và rủi ro khi thay đổi.

---


---

## Conclusion

Kiến trúc microservices giúp hệ thống quản lý nghỉ phép đạt được tính linh hoạt, mở rộng, dễ bảo trì và đảm bảo an toàn dữ liệu. Việc tách biệt từng chức năng thành các service độc lập giúp phát triển song song, nâng cấp từng phần mà không ảnh hưởng đến toàn bộ hệ thống. API Gateway đóng vai trò trung tâm trong việc bảo vệ, định tuyến và cân bằng tải cho toàn bộ hệ thống.
