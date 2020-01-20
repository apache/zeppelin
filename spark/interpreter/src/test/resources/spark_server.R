# Define server logic to summarize and view selected dataset ----
server <- function(input, output) {

    # Return the requested dataset ----
    datasetInput <- reactive({
        switch(input$dataset,
        "rock" = as.DataFrame(rock),
        "pressure" = as.DataFrame(pressure),
        "cars" = as.DataFrame(cars))
    })

    # Generate a summary of the dataset ----
    output$summary <- renderPrint({
        dataset <- datasetInput()
        showDF(summary(dataset))
    })

    # Show the first "n" observations ----
    output$view <- renderTable({
        head(datasetInput(), n = input$obs)
    })

}