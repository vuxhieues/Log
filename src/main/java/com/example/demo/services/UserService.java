package com.example.demo.services;

import com.example.demo.entities.CurrentUserSession;
import com.example.demo.entities.Login;
import com.example.demo.entities.User;
import com.example.demo.exceptions.CurrentUserException;

public interface UserService {
    public User addUser(User user);
    public User updateUser(User user, int id);
    public User deleteUser(int id);
    public User findUser(int id);
    public CurrentUserSession logIn(Login login) throws CurrentUserException;
}
