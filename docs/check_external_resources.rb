#!/usr/bin/env ruby
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

require "uri"

SITE_DIR = ARGV.fetch(0, "_site")
RESOURCE_TAGS = %w[script img iframe link source video audio embed object].freeze
RESOURCE_PATTERN =
  /<(#{RESOURCE_TAGS.join("|")})\b[^>]*\b(?:src|href|data)\s*=\s*["']([^"']+)["']/i
CSS_URL_PATTERN = /url\(\s*["']?([^"')]+)["']?\s*\)/i
TRACKER_PATTERN =
  /google-analytics|googletag|gtag\s*\(|mixpanel|getclicky|piwik|disqus|connect\.facebook|(?:platform\.)?twitter\.com\/widgets/i
MATOMO_CODE_PATTERN = /matomo\.(?:js|php)|setTrackerUrl|\b_paq\b/i
ASF_MATOMO_URL = "https://analytics.apache.org/"

def remote_host(value)
  return nil unless value.match?(%r{\A(?:https?:)?//}i)

  normalized = value.start_with?("//") ? "https:#{value}" : value
  URI.parse(normalized).host || :invalid
rescue URI::InvalidURIError
  :invalid
end

def asf_host?(host)
  host != :invalid && (host == "apache.org" || host.end_with?(".apache.org"))
end

violations = []

Dir.glob(File.join(SITE_DIR, "**", "*.html")).sort.each do |file|
  File.read(file).scan(RESOURCE_PATTERN) do |tag, value|
    host = remote_host(value)
    next if host.nil? || asf_host?(host)

    violations << "#{file}: external #{tag} resource #{value}"
  end
end

Dir.glob(File.join(SITE_DIR, "**", "*.css")).sort.each do |file|
  File.read(file).scan(CSS_URL_PATTERN) do |match|
    value = match.first
    host = remote_host(value)
    next if host.nil? || asf_host?(host)

    violations << "#{file}: external CSS resource #{value}"
  end
end

Dir.glob(File.join(SITE_DIR, "**", "*.{html,js,css}")).sort.each do |file|
  content = File.read(file)
  violations << "#{file}: tracker or external embed code" if content.match?(TRACKER_PATTERN)
  if content.match?(MATOMO_CODE_PATTERN) && !content.include?(ASF_MATOMO_URL)
    violations << "#{file}: Matomo must use #{ASF_MATOMO_URL}"
  end
end

if violations.empty?
  puts "No disallowed external resources or trackers found in #{SITE_DIR}"
  exit 0
end

warn violations.join("\n")
exit 1
