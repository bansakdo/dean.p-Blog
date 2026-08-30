package com.deanp.blog.post.service;

import com.deanp.blog.post.PostView;
import com.deanp.blog.post.SamplePostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PostService {

    private final SamplePostRepository posts;

    public List<PostView> findAll() {
        return posts.findAll();
    }

    public Optional<PostView> findBySlug(String slug) {
        return posts.findBySlug(slug);
    }
}
