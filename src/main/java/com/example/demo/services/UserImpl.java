package com.example.demo.services;

import com.example.demo.entities.CurrentUserSession;
import com.example.demo.entities.Login;
import com.example.demo.entities.User;
import com.example.demo.exceptions.CurrentUserException;
import com.example.demo.repositories.SessionRepo;
import com.example.demo.repositories.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Random;

@Service
public class UserImpl implements UserService{
    @Autowired
    private UserRepo userRepo;

    @Autowired
    private SessionRepo sessionRepo;

    @Override
    public User addUser(User user) {
        User user1 = userRepo.save(user);
        return user1;
    }

    @Override
    public User updateUser(User user, String id)throws CurrentUserException {
        User user2 = this.findUserBySessionId(id);
        if(user2 != null) {
            user2.setName(user.getName());
            user2.setEmail(user.getEmail());
            user2.setPassword(user.getPassword());
            return userRepo.save(user2);
        }
        else{
            throw new CurrentUserException("Không tồn tại người dùng");
        }
    }

    @Override
    public String deleteUser(String id) throws CurrentUserException{
        User user1 = this.findUserBySessionId(id);
        if(user1 != null) {
            String name = user1.getName();
            userRepo.delete(user1);
            this.logOut(id);
            return "Đã xóa thành công người dùng" + " " + name;
        }
        else{
            throw new CurrentUserException("Người dùng không tồn tại");
        }
    }

    @Override
    public User findUser(String id) throws CurrentUserException {
        User user = this.findUserBySessionId(id);
        if(user != null) {
            return user;
        }
        else{
            throw new CurrentUserException("Người dùng không tồn tại");
        }
    }

    @Override
    public CurrentUserSession logIn(Login login) throws CurrentUserException {

        Optional<User>user = userRepo.findByEmail(login.getEmail());
        if(user.isPresent()) {
            User currentUser = user.get();
            if(currentUser.getPassword().equals(login.getPassword())) {
                Optional<CurrentUserSession> session = sessionRepo.findById(login.getEmail());
                if(session.isEmpty()){
                    String key = random();
                    CurrentUserSession currentUserSession = new CurrentUserSession(login.getEmail(), currentUser.getUserId(), key);
                    return sessionRepo.save(currentUserSession);
                }
                else{
                    throw new CurrentUserException("Tài khoản đang được đăng nhập ở nơi khác");
                }
            }
            else{
                throw new CurrentUserException("Sai mật khẩu");
            }
        }
        else{
            throw new CurrentUserException("Sai email");
        }
    }

    @Override
    public String logOut(String SessionId) throws CurrentUserException {
        Optional<CurrentUserSession> session = sessionRepo.findBySessionId(SessionId);
        if(session.isPresent()) {
            CurrentUserSession currentUserSession = session.get();
            sessionRepo.delete(currentUserSession);
            return "Đăng xuất thành công";
        }
        else{
            throw new CurrentUserException("Lỗi session");
        }
    }

    private String random(){
        String secret = "abcdefjhijklnmopqrstuvwxyzABCDEFGHIJKLNMOPQRSTUVWXYZ123456789";
        int length = 6;
        StringBuilder s = new StringBuilder();
        Random random = new Random();
        for(int i=0; i<6; i++){
            int index = random.nextInt(secret.length());
            s.append(secret.charAt(index));
        }
        return s.toString();
    }

    private User findUserBySessionId(String sessionId) throws CurrentUserException {
        Optional<CurrentUserSession>session = sessionRepo.findBySessionId(sessionId);
        if(session.isPresent()) {
            CurrentUserSession currentUserSession = session.get();
            Optional<User> user = userRepo.findByEmail(currentUserSession.getEmail());
            return user.get();
        }
        else{
            throw new CurrentUserException("Sai session");
        }
    }
}
