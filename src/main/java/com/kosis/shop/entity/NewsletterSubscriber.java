package com.kosis.shop.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Email collected by the "Join the KOSIS Community" form. */
@Entity
@Table(name = "newsletter_subscribers")
public class NewsletterSubscriber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 190)
    private String email;

    @Column(name = "subscribed_at", nullable = false)
    private LocalDateTime subscribedAt = LocalDateTime.now();

    public NewsletterSubscriber() {
    }

    public NewsletterSubscriber(String email) {
        this.email = email;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public LocalDateTime getSubscribedAt() { return subscribedAt; }
    public void setSubscribedAt(LocalDateTime subscribedAt) { this.subscribedAt = subscribedAt; }
}
