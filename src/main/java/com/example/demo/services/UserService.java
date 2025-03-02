package com.example.demo.services;

import com.example.demo.entities.CurrentUserSession;
import com.example.demo.entities.Login;
import com.example.demo.entities.User;
import com.example.demo.exceptions.CurrentUserException;
import com.example.demo.exceptions.UserException;

public interface UserService {
    public User addUser(User user) throws UserException;
    public User updateUser(User user, String sessionId) throws CurrentUserException;
    public String deleteUser(String sessionId, Integer userId) throws CurrentUserException, UserException;
    public User findUser(String sessionId) throws CurrentUserException;
    public CurrentUserSession logIn(Login login) throws CurrentUserException;
    public String logOut(String SessionId) throws CurrentUserException;
}
