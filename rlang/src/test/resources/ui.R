# Define UI for dataset viewer app ----
ui <- fluidPage(

# App title ----
titlePanel("Shiny Text"),

# Sidebar layout with a input and output definitions ----
sidebarLayout(

# Sidebar panel for inputs ----
sidebarPanel(

# Input: Selector for choosing dataset ----
selectInput(inputId = "dataset",
label = "Choose a dataset:",
choices = c("rock", "pressure", "cars")),

# Input: Numeric entry for number of obs to view ----
numericInput(inputId = "obs",
label = "Number of observations to view:",
value = 10)
),

# Main panel for displaying outputs ----
mainPanel(

# Output: Verbatim text for data summary ----
verbatimTextOutput("summary"),

# Output: HTML table with requested number of observations ----
tableOutput("view")

)
)
)