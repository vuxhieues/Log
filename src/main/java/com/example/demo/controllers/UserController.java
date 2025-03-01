package com.example.demo.controllers;

import com.example.demo.entities.User;
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

    @PutMapping("/update")
    public ResponseEntity<User> updateUser(@RequestBody User user, @PathVariable("UserId") Integer id) {
        User user1 = userService.updateUser(user, id);
        return new ResponseEntity<User>(user1, HttpStatus.OK);
    }

    @DeleteMapping("/delete/{UserId}")
    public  ResponseEntity<User> deleteUser(@PathVariable("UserId") Integer id) {
        User user = userService.deleteUser(id);
        return new ResponseEntity<User>(user, HttpStatus.OK);
    }

    @GetMapping("/get/{UserId}")
    public  ResponseEntity<User> getUser(@PathVariable("UserId") Integer id) {
        User user = userService.findUser(id);
        return new ResponseEntity<User>(user, HttpStatus.OK);
    }
 }
