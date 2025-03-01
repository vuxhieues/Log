package com.example.demo.controllers;

import com.example.demo.entities.CurrentUserSession;
import com.example.demo.entities.Login;
import com.example.demo.entities.User;
import com.example.demo.exceptions.CurrentUserException;
import com.example.demo.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/user")
@RestController
public class UserController {
    @Autowired
    private UserService userService;

    @PostMapping("/add")
    public ResponseEntity<User> addUser(@RequestBody User user) {
        User user1 = userService.addUser(user);
        return new ResponseEntity<User>(user1, HttpStatus.CREATED);
    }

    @PutMapping("/update/{SessionId}")
    public ResponseEntity<User> updateUser(@RequestBody User user, @PathVariable("SessionId") String id) throws CurrentUserException {
        User user1 = userService.updateUser(user, id);
        return new ResponseEntity<User>(user1, HttpStatus.OK);
    }

    @DeleteMapping("/delete/{SessionId}")
    public  ResponseEntity<String> deleteUser(@PathVariable("SessionId") String id) throws CurrentUserException {
        String s = userService.deleteUser(id);
        return new ResponseEntity<String>(s, HttpStatus.OK);
    }

    @GetMapping("/get/{SessionId}")
    public  ResponseEntity<User> getUser(@PathVariable("SessionId") String id) throws CurrentUserException {
        User user = userService.findUser(id);
        return new ResponseEntity<User>(user, HttpStatus.OK);
    }

    @PostMapping("/login")
    public ResponseEntity<CurrentUserSession> logIn(@RequestBody Login login) throws CurrentUserException {
        CurrentUserSession session = userService.logIn(login);
        return new ResponseEntity<CurrentUserSession>(session, HttpStatus.OK);
    }

    @DeleteMapping("/logout/{SessionId}")
    public  ResponseEntity<String> logOut(@PathVariable("SessionId") String id) throws CurrentUserException {
        String message = userService.logOut(id);
        return new ResponseEntity<String>(message, HttpStatus.OK);
    }
 }
