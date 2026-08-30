package com.deanp.blog.web;

import com.deanp.blog.post.PostView;
import com.deanp.blog.post.SamplePostRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Controller
public class BlogController {

    private final SamplePostRepository posts;

    public BlogController(SamplePostRepository posts) {
        this.posts = posts;
    }

    @GetMapping("/")
    public String home(Model model) {
        List<PostView> allPosts = posts.findAll();
        model.addAttribute("featuredPost", allPosts.getFirst());
        model.addAttribute("recentPosts", allPosts.stream().skip(1).toList());
        model.addAttribute("pageTitle", "dean.p — 개발과 기록");
        return "home";
    }

    @GetMapping("/posts")
    public String posts(Model model) {
        model.addAttribute("posts", posts.findAll());
        model.addAttribute("pageTitle", "글 — dean.p");
        return "posts";
    }

    @GetMapping("/posts/{slug}")
    public String post(@PathVariable String slug, Model model) {
        PostView post = posts.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("post", post);
        model.addAttribute("pageTitle", post.title() + " — dean.p");
        return "post";
    }

    @GetMapping("/about")
    public String about(Model model) {
        model.addAttribute("pageTitle", "소개 — dean.p");
        return "about";
    }
}
