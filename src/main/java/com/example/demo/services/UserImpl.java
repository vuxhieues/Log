package com.example.demo.services;

import com.example.demo.entities.CurrentUserSession;
import com.example.demo.entities.Login;
import com.example.demo.entities.User;
import com.example.demo.exceptions.CurrentUserException;
import com.example.demo.repositories.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserImpl implements UserService{
    @Autowired
    private UserRepo userRepo;

    @Override
    public User addUser(User user) {
        User user1 = userRepo.save(user);
        return user1;
    }

    @Override
    public User updateUser(User user, int id) {
        Optional<User> user1 = userRepo.findById(id);
        User user2 = user1.get();
        user2.setName(user.getName());
        user2.setEmail(user.getEmail());
        user2.setPassword(user.getPassword());
        userRepo.save(user2);
        return user2;
    }

    @Override
    public User deleteUser(int id) {
        Optional<User> user = userRepo.findById(id);
        User user1 = user.get();
        userRepo.delete(user1);
        return user1;
    }

    @Override
    public User findUser(int id) {
        Optional<User> user = userRepo.findById(id);
        return user.get();
    }

    @Override
    public CurrentUserSession logIn(Login login) throws CurrentUserException {
        return null;
    }
}
