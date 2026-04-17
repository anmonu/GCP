def imageCatalog = [
  "https://images.unsplash.com/photo-1506744038136-46273834b3fb",
  "https://images.unsplash.com/photo-1500530855697-b586d89ba3ee",
  "https://images.unsplash.com/photo-1519125323398-675f0ddb6308"
]

println "Random image catalog for demo deployments:"
imageCatalog.eachWithIndex { imageUrl, idx ->
  println "${idx + 1}. ${imageUrl}"
}
